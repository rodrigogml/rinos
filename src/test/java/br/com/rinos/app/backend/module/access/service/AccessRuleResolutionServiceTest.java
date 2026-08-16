package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.entity.AccessKeyEntity;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;
import br.com.rinos.app.backend.module.access.enums.AccessRecordStatus;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.AccessKeyRepository;
import br.com.rinos.app.backend.module.access.repository.AccessRuleRepository;

class AccessRuleResolutionServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final AccessKeyDescriptor KEY = AccessControlAccessKeys.TENANT_RULE_VIEW;

  private final AccessKeyRegistryService registry = mock(AccessKeyRegistryService.class);
  private final AccessContextRevisionService revisionService =
      mock(AccessContextRevisionService.class);
  private final AccessRuleRepository ruleRepository = mock(AccessRuleRepository.class);
  private final AccessGroupRepository groupRepository = mock(AccessGroupRepository.class);
  private final AccessGroupSubjectRepository subjectRepository =
      mock(AccessGroupSubjectRepository.class);
  private final AccessKeyRepository keyRepository = mock(AccessKeyRepository.class);

  private AccessRuleResolutionService service;

  @BeforeEach
  void setUp() {
    service = new AccessRuleResolutionService(
        registry, revisionService, ruleRepository, groupRepository, subjectRepository,
        keyRepository, new AuthorizationSnapshotCache(10_000, java.time.Duration.ofMinutes(30)));
    when(registry.find(KEY.code())).thenReturn(Optional.of(KEY));
    when(revisionService.current(AccessScope.TENANT, 42L)).thenReturn(7L);
    when(keyRepository.findByCode(KEY.code())).thenReturn(Optional.of(keyEntity(5L, KEY)));
  }

  @Test
  void resolveHuman_shouldLetGroupBlockWinOverDirectPermit() {
    AccessRuleEntity permit = membershipRule(1L, 5L, AccessRuleEffect.PERMITIR, null, null);
    AccessGroupSubjectEntity subject = groupSubject(8L, 77L, null, null);
    AccessGroupEntity group = group(8L, 42L);
    AccessRuleEntity block = groupRule(2L, 8L, 5L, AccessRuleEffect.BLOQUEAR, null, null);
    when(ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L)).thenReturn(List.of(permit));
    when(subjectRepository.findByAccountMembershipId(77L)).thenReturn(List.of(subject));
    when(groupRepository.findAllById(List.of(8L))).thenReturn(List.of(group));
    when(ruleRepository.findByScopeAndTenantIdAndAccessGroupIdIn(
        AccessScope.TENANT, 42L, List.of(8L))).thenReturn(List.of(block));

    ResolvedAccessSnapshot snapshot = service.resolveHuman(request(Set.of(KEY)), NOW);

    assertThat(snapshot.contextRevision()).isEqualTo(7);
    assertThat(snapshot.keyResults()).singleElement().satisfies(result -> {
      assertThat(result.allowed()).isFalse();
      assertThat(result.permitSources()).hasSize(1);
      assertThat(result.blockingSources()).hasSize(1);
      assertThat(result.missingPermit()).isFalse();
    });
  }

  @Test
  void resolveHuman_shouldIgnoreFutureRuleAndExposeNextBoundary() {
    Instant future = NOW.plusSeconds(600);
    AccessRuleEntity permit = membershipRule(
        1L, 5L, AccessRuleEffect.PERMITIR, future, future.plusSeconds(600));
    when(ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L)).thenReturn(List.of(permit));
    when(subjectRepository.findByAccountMembershipId(77L)).thenReturn(List.of());
    when(groupRepository.findAllById(List.of())).thenReturn(List.of());

    ResolvedAccessSnapshot snapshot = service.resolveHuman(request(Set.of(KEY)), NOW);

    assertThat(snapshot.nextTemporalBoundary()).isEqualTo(future);
    assertThat(snapshot.keyResults()).singleElement().satisfies(result -> {
      assertThat(result.allowed()).isFalse();
      assertThat(result.missingPermit()).isTrue();
      assertThat(result.ignoredSources()).hasSize(1);
    });
  }

  @Test
  void resolveHuman_shouldLetDirectBlockWinOverGroupPermit() {
    AccessRuleEntity block = membershipRule(1L, 5L, AccessRuleEffect.BLOQUEAR, null, null);
    AccessGroupSubjectEntity subject = groupSubject(8L, 77L, null, null);
    AccessGroupEntity group = group(8L, 42L);
    AccessRuleEntity permit = groupRule(2L, 8L, 5L, AccessRuleEffect.PERMITIR, null, null);
    when(ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L)).thenReturn(List.of(block));
    when(subjectRepository.findByAccountMembershipId(77L)).thenReturn(List.of(subject));
    when(groupRepository.findAllById(List.of(8L))).thenReturn(List.of(group));
    when(ruleRepository.findByScopeAndTenantIdAndAccessGroupIdIn(
        AccessScope.TENANT, 42L, List.of(8L))).thenReturn(List.of(permit));

    ResolvedAccessSnapshot snapshot = service.resolveHuman(request(Set.of(KEY)), NOW);

    assertThat(snapshot.keyResults()).singleElement().satisfies(result -> {
      assertThat(result.allowed()).isFalse();
      assertThat(result.permitSources()).hasSize(1);
      assertThat(result.blockingSources()).hasSize(1);
    });
  }

  @Test
  void resolveHuman_shouldNotUseGroupFromAnotherTenant() {
    AccessGroupSubjectEntity subject = groupSubject(8L, 77L, null, null);
    AccessGroupEntity foreignGroup = group(8L, 99L);
    AccessRuleEntity foreignPermit =
        groupRule(2L, 8L, 5L, AccessRuleEffect.PERMITIR, null, null);
    when(ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L)).thenReturn(List.of());
    when(subjectRepository.findByAccountMembershipId(77L)).thenReturn(List.of(subject));
    when(groupRepository.findAllById(List.of(8L))).thenReturn(List.of(foreignGroup));
    when(ruleRepository.findByScopeAndTenantIdAndAccessGroupIdIn(
        AccessScope.TENANT, 42L, List.of(8L))).thenReturn(List.of(foreignPermit));

    ResolvedAccessSnapshot snapshot = service.resolveHuman(request(Set.of(KEY)), NOW);

    assertThat(snapshot.keyResults()).singleElement().satisfies(result -> {
      assertThat(result.allowed()).isFalse();
      assertThat(result.missingPermit()).isTrue();
      assertThat(result.permitSources()).isEmpty();
    });
  }

  @Test
  void resolveHuman_shouldLetOneBlockingGroupWinOverSeveralPermittingGroups() {
    List<Long> groupIds = List.of(8L, 9L, 10L);
    List<AccessGroupSubjectEntity> subjects = groupIds.stream()
        .map(groupId -> groupSubject(groupId, 77L, null, null))
        .toList();
    List<AccessGroupEntity> groups = groupIds.stream().map(id -> group(id, 42L)).toList();
    List<AccessRuleEntity> rules = List.of(
        groupRule(1L, 8L, 5L, AccessRuleEffect.PERMITIR, null, null),
        groupRule(2L, 9L, 5L, AccessRuleEffect.PERMITIR, null, null),
        groupRule(3L, 10L, 5L, AccessRuleEffect.BLOQUEAR, null, null));
    when(ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L)).thenReturn(List.of());
    when(subjectRepository.findByAccountMembershipId(77L)).thenReturn(subjects);
    when(groupRepository.findAllById(groupIds)).thenReturn(groups);
    when(ruleRepository.findByScopeAndTenantIdAndAccessGroupIdIn(
        AccessScope.TENANT, 42L, groupIds)).thenReturn(rules);

    ResolvedAccessSnapshot snapshot = service.resolveHuman(request(Set.of(KEY)), NOW);

    assertThat(snapshot.keyResults()).singleElement().satisfies(result -> {
      assertThat(result.allowed()).isFalse();
      assertThat(result.permitSources()).hasSize(2);
      assertThat(result.blockingSources()).hasSize(1);
    });
  }

  @Test
  void resolveHuman_shouldConfirmRevisionOnEveryOperationAndReuseLoadedSources() {
    AccessRuleEntity permit = membershipRule(1L, 5L, AccessRuleEffect.PERMITIR, null, null);
    when(ruleRepository.findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L)).thenReturn(List.of(permit));
    when(subjectRepository.findByAccountMembershipId(77L)).thenReturn(List.of());
    when(groupRepository.findAllById(List.of())).thenReturn(List.of());

    service.resolveHuman(request(Set.of(KEY)), NOW);
    service.resolveHuman(request(Set.of(KEY)), NOW.plusSeconds(1));

    verify(revisionService, times(3)).current(AccessScope.TENANT, 42L);
    verify(ruleRepository, times(1)).findByScopeAndTenantIdAndAccountMembershipId(
        AccessScope.TENANT, 42L, 77L);
  }

  private static AuthorizationRequest request(Set<AccessKeyDescriptor> keys) {
    return new AuthorizationRequest(
        AuthorizationActor.human(11L), 77L, AuthorizationContext.tenant(42L),
        "access.test", keys,
        new AuthenticationAssurance(
            AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
            NOW.minusSeconds(60), NOW.minusSeconds(60)),
        false, AuthorizationExplanationMode.SAFE);
  }

  private static AccessKeyEntity keyEntity(Long id, AccessKeyDescriptor descriptor) {
    AccessKeyEntity key = new AccessKeyEntity(
        descriptor.code(), descriptor.scope(), 3L, descriptor.ownerModule(),
        descriptor.nameI18nKey(), descriptor.descriptionI18nKey(), null,
        AccessRecordStatus.ACTIVE, 1);
    ReflectionTestUtils.setField(key, "id", id);
    return key;
  }

  private static AccessRuleEntity membershipRule(Long id, Long keyId, AccessRuleEffect effect,
      Instant validFrom, Instant validUntil) {
    AccessRuleEntity rule = new AccessRuleEntity(
        AccessScope.TENANT, 42L, AccessRuleOriginType.DIRECT_MEMBERSHIP, null, 77L, null,
        keyId, effect, validFrom, validUntil, 9L);
    ReflectionTestUtils.setField(rule, "id", id);
    return rule;
  }

  private static AccessRuleEntity groupRule(Long id, Long groupId, Long keyId,
      AccessRuleEffect effect, Instant validFrom, Instant validUntil) {
    AccessRuleEntity rule = new AccessRuleEntity(
        AccessScope.TENANT, 42L, AccessRuleOriginType.GROUP, null, null, groupId,
        keyId, effect, validFrom, validUntil, 9L);
    ReflectionTestUtils.setField(rule, "id", id);
    return rule;
  }

  private static AccessGroupSubjectEntity groupSubject(
      Long groupId, Long membershipId, Instant validFrom, Instant validUntil) {
    return new AccessGroupSubjectEntity(
        groupId, null, membershipId, validFrom, validUntil, 9L);
  }

  private static AccessGroupEntity group(Long id, Long tenantId) {
    AccessGroupEntity group = new AccessGroupEntity(
        AccessScope.TENANT, tenantId, "Readers", "readers", null, false, null);
    ReflectionTestUtils.setField(group, "id", id);
    return group;
  }
}
