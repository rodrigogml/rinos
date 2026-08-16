package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleHistoryEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineEntity;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineKeyId;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleHistoryRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineKeyRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision;

class AccessRuleMutationServiceTest {

  private final AccessKeyRepository keyRepository = mock(AccessKeyRepository.class);
  private final AccessGroupRepository groupRepository = mock(AccessGroupRepository.class);
  private final AccessRuleRepository ruleRepository = mock(AccessRuleRepository.class);
  private final AccessRuleHistoryRepository historyRepository =
      mock(AccessRuleHistoryRepository.class);
  private final AccessAuditEventRepository auditRepository = mock(AccessAuditEventRepository.class);
  private final AccessContextRevisionService revisionService =
      mock(AccessContextRevisionService.class);
  private final ProtectedGroupBaselineRepository baselineRepository =
      mock(ProtectedGroupBaselineRepository.class);
  private final ProtectedGroupBaselineKeyRepository baselineKeyRepository =
      mock(ProtectedGroupBaselineKeyRepository.class);
  private final AdministrativeContinuityEvaluator continuity =
      mock(AdministrativeContinuityEvaluator.class);
  private final AccessContextCacheInvalidationService cacheInvalidationService =
      mock(AccessContextCacheInvalidationService.class);

  private AccessRuleMutationService service;

  @BeforeEach
  void setUp() {
    when(continuity.evaluateContext(any(), any(), any()))
        .thenReturn(MembershipContinuityDecision.permit());
    service = new AccessRuleMutationService(
        keyRepository, groupRepository, ruleRepository, historyRepository, auditRepository,
        revisionService, baselineRepository, baselineKeyRepository, cacheInvalidationService, continuity);
  }

  @Test
  void apply_shouldPersistRuleHistoryAuditAndRevisionInOrder() {
    AccessKeyEntity key = activeKey(AccessScope.GLOBAL);
    when(keyRepository.findByCode("global.platform.directory.view")).thenReturn(Optional.of(key));
    when(ruleRepository.findByScopeAndTenantIdAndUserIdAndAccessKeyId(
        AccessScope.GLOBAL, null, 11L, 7L)).thenReturn(Optional.empty());
    when(ruleRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      AccessRuleEntity rule = invocation.getArgument(0);
      ReflectionTestUtils.setField(rule, "id", 19L);
      return rule;
    });
    when(revisionService.lockAndIncrement(AccessScope.GLOBAL, null)).thenReturn(4L);

    AccessRuleMutationResult result = service.apply(globalDirectCommand());

    assertThat(result).isEqualTo(new AccessRuleMutationResult(19L, 4L, true));
    InOrder order = inOrder(
        ruleRepository, historyRepository, auditRepository, revisionService,
        cacheInvalidationService);
    order.verify(revisionService).lock(AccessScope.GLOBAL, null);
    order.verify(ruleRepository).saveAndFlush(any(AccessRuleEntity.class));
    order.verify(historyRepository).save(any(AccessRuleHistoryEntity.class));
    order.verify(auditRepository).save(any());
    order.verify(revisionService).lockAndIncrement(AccessScope.GLOBAL, null);
    order.verify(cacheInvalidationService).afterCommit(AccessScope.GLOBAL, null);

    ArgumentCaptor<AccessRuleHistoryEntity> history =
        ArgumentCaptor.forClass(AccessRuleHistoryEntity.class);
    org.mockito.Mockito.verify(historyRepository).save(history.capture());
    assertThat(history.getValue().getPreviousSnapshot()).isNull();
    assertThat(history.getValue().getNewSnapshot())
        .contains("\"effect\":\"PERMITIR\"", "\"userId\":11");
  }

  @Test
  void apply_shouldRejectBlockingRuleFromProtectedGroupBeforeAnyMutation() {
    AccessKeyEntity key = activeKey(AccessScope.TENANT);
    AccessGroupEntity group = new AccessGroupEntity(
        AccessScope.TENANT, 42L, "Administradores", "administradores", null, true, 1);
    ReflectionTestUtils.setField(group, "id", 8L);
    when(keyRepository.findByCode("tenant.foundation.access.manage"))
        .thenReturn(Optional.of(key));
    when(groupRepository.findById(8L)).thenReturn(Optional.of(group));
    ProtectedGroupBaselineEntity baseline =
        new ProtectedGroupBaselineEntity(AccessScope.TENANT, 1);
    ReflectionTestUtils.setField(baseline, "id", 6L);
    when(baselineRepository.findByScopeAndBaselineVersion(AccessScope.TENANT, 1))
        .thenReturn(Optional.of(baseline));
    when(baselineKeyRepository.existsById(new ProtectedGroupBaselineKeyId(6L, 7L)))
        .thenReturn(true);
    AccessRuleMutationCommand command = new AccessRuleMutationCommand(
        AccessScope.TENANT, 42L, AccessRuleOriginType.GROUP, null, null, 8L,
        "tenant.foundation.access.manage", AccessRuleEffect.BLOQUEAR, null, null,
        9L, null, "not allowed", "correlation-2", Instant.parse("2026-08-15T12:00:00Z"));

    assertThatIllegalArgumentException().isThrownBy(() -> service.apply(command))
        .withMessageContaining("baseline keys");
    verifyNoInteractions(
        ruleRepository, historyRepository, auditRepository,
        cacheInvalidationService);
  }

  @Test
  void deactivate_shouldPreserveHistoryAndAdvanceRevision() {
    AccessRuleEntity rule = new AccessRuleEntity(
        AccessScope.GLOBAL, null, AccessRuleOriginType.DIRECT_USER, 11L, null, null, 7L,
        AccessRuleEffect.PERMITIR, null, null, 9L);
    ReflectionTestUtils.setField(rule, "id", 19L);
    when(ruleRepository.findById(19L)).thenReturn(Optional.of(rule));
    when(ruleRepository.saveAndFlush(rule)).thenReturn(rule);
    when(revisionService.lockAndIncrement(AccessScope.GLOBAL, null)).thenReturn(5L);

    AccessRuleMutationResult result = service.deactivate(new AccessRuleDeactivationCommand(
        19L, AccessScope.GLOBAL, null, new AccessMutationMetadata(
            9L, null, "remove", "correlation-3",
            Instant.parse("2026-08-15T12:00:00Z"))));

    assertThat(result).isEqualTo(new AccessRuleMutationResult(19L, 5L, true));
    assertThat(rule.getStatus()).isEqualTo(AccessRecordStatus.INACTIVE);
    ArgumentCaptor<AccessRuleHistoryEntity> history =
        ArgumentCaptor.forClass(AccessRuleHistoryEntity.class);
    org.mockito.Mockito.verify(historyRepository).save(history.capture());
    assertThat(history.getValue().getChangeType())
        .isEqualTo(br.com.rinos.app.backend.module.access.enums.AccessRuleChangeType.DEACTIVATE);
    assertThat(history.getValue().getNewSnapshot()).contains("\"status\":\"INACTIVE\"");
    org.mockito.Mockito.verify(cacheInvalidationService)
        .afterCommit(AccessScope.GLOBAL, null);
  }

  private static AccessKeyEntity activeKey(AccessScope scope) {
    String code = scope == AccessScope.GLOBAL
        ? "global.platform.directory.view"
        : "tenant.foundation.access.manage";
    AccessKeyEntity key = new AccessKeyEntity(
        code, scope, 3L, "access-control", "name", "description", null, null,
        AccessRecordStatus.ACTIVE, 1);
    ReflectionTestUtils.setField(key, "id", 7L);
    return key;
  }

  private static AccessRuleMutationCommand globalDirectCommand() {
    return new AccessRuleMutationCommand(
        AccessScope.GLOBAL, null, AccessRuleOriginType.DIRECT_USER, 11L, null, null,
        "global.platform.directory.view", AccessRuleEffect.PERMITIR, null, null,
        9L, null, "bootstrap", "correlation-1", Instant.parse("2026-08-15T12:00:00Z"));
  }
}
