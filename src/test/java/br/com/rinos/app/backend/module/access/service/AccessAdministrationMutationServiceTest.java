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
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;
import br.com.rinos.app.backend.module.access.repository.AccessAuditEventRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupRepository;
import br.com.rinos.app.backend.module.access.repository.AccessGroupSubjectRepository;
import br.com.rinos.app.backend.module.access.repository.ProtectedGroupBaselineRepository;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision;

class AccessAdministrationMutationServiceTest {

  private final AccessGroupRepository groupRepository = mock(AccessGroupRepository.class);
  private final AccessGroupSubjectRepository subjectRepository =
      mock(AccessGroupSubjectRepository.class);
  private final ProtectedGroupBaselineRepository baselineRepository =
      mock(ProtectedGroupBaselineRepository.class);
  private final AccessAuditEventRepository auditRepository = mock(AccessAuditEventRepository.class);
  private final AccessContextRevisionService revisionService =
      mock(AccessContextRevisionService.class);
  private final AccessContextCacheInvalidationService invalidationService =
      mock(AccessContextCacheInvalidationService.class);
  private final AccountMembershipAccessPort membershipPort =
      mock(AccountMembershipAccessPort.class);
  private final AdministrativeContinuityEvaluator continuity =
      mock(AdministrativeContinuityEvaluator.class);

  private AccessAdministrationMutationService service;

  @BeforeEach
  void setUp() {
    when(continuity.evaluateContext(any(), any(), any()))
        .thenReturn(MembershipContinuityDecision.permit());
    service = new AccessAdministrationMutationService(
        groupRepository, subjectRepository, baselineRepository, auditRepository,
        revisionService, invalidationService, membershipPort, continuity);
  }

  @Test
  void createGroup_shouldNormalizeAuditAdvanceRevisionAndInvalidate() {
    when(groupRepository.findByScopeAndTenantIdAndNormalizedName(
        AccessScope.GLOBAL, null, "administracao geral")).thenReturn(Optional.empty());
    when(groupRepository.saveAndFlush(any())).thenAnswer(invocation -> {
      AccessGroupEntity group = invocation.getArgument(0);
      ReflectionTestUtils.setField(group, "id", 8L);
      return group;
    });
    when(revisionService.lockAndIncrement(AccessScope.GLOBAL, null)).thenReturn(3L);

    AccessAdministrationMutationResult result = service.mutateGroup(new AccessGroupMutationCommand(
        AccessAdministrationAction.CREATE, null, AccessScope.GLOBAL, null,
        "  Administração   Geral ", " Grupo global ", false, null, metadata()));

    assertThat(result).isEqualTo(new AccessAdministrationMutationResult(8L, 3L, true));
    InOrder order = inOrder(
        groupRepository, auditRepository, revisionService, invalidationService);
    order.verify(revisionService).lock(AccessScope.GLOBAL, null);
    order.verify(groupRepository).saveAndFlush(any(AccessGroupEntity.class));
    order.verify(auditRepository).save(any());
    order.verify(revisionService).lockAndIncrement(AccessScope.GLOBAL, null);
    order.verify(invalidationService).afterCommit(AccessScope.GLOBAL, null);
  }

  @Test
  void assignSubject_shouldRejectMembershipFromAnotherTenantWithoutMutation() {
    AccessGroupEntity group = new AccessGroupEntity(
        AccessScope.TENANT, 42L, "Financeiro", "financeiro", null, false, null);
    ReflectionTestUtils.setField(group, "id", 9L);
    when(groupRepository.findMutationContext(9L))
        .thenReturn(Optional.of(new AccessMutationContext(AccessScope.TENANT, 42L)));
    when(groupRepository.findById(9L)).thenReturn(Optional.of(group));
    when(membershipPort.inspect(77L)).thenReturn(
        AccountMembershipAccessSnapshot.found(11L, 99L, true, true));
    AccessGroupSubjectMutationCommand command = new AccessGroupSubjectMutationCommand(
        AccessAdministrationAction.ASSIGN, null, 9L, null, 77L, null, null, metadata());

    assertThatIllegalArgumentException().isThrownBy(() -> service.mutateSubject(command))
        .withMessageContaining("another context");
    verifyNoInteractions(subjectRepository, auditRepository, invalidationService);
  }

  @Test
  void endSubject_shouldRejectWhenGlobalContinuityWouldBeLost() {
    AccessGroupEntity group = new AccessGroupEntity(
        AccessScope.GLOBAL, null, "Administradores", "administradores", null, true, 1);
    ReflectionTestUtils.setField(group, "id", 9L);
    AccessGroupSubjectEntity subject = new AccessGroupSubjectEntity(
        9L, 11L, null, null, null, 12L);
    ReflectionTestUtils.setField(subject, "id", 10L);
    when(subjectRepository.findMutationContext(10L))
        .thenReturn(Optional.of(new AccessMutationContext(AccessScope.GLOBAL, null)));
    when(subjectRepository.findById(10L)).thenReturn(Optional.of(subject));
    when(groupRepository.findById(9L)).thenReturn(Optional.of(group));
    when(continuity.evaluateContext(AccessScope.GLOBAL, null, metadata().occurredAt()))
        .thenReturn(MembershipContinuityDecision.deny());

    assertThatIllegalArgumentException().isThrownBy(() -> service.mutateSubject(
        new AccessGroupSubjectMutationCommand(
            AccessAdministrationAction.END, 10L, null, null, null, null, null, metadata())))
        .withMessageContaining("continuity would be lost");
    verifyNoInteractions(auditRepository, invalidationService);
  }

  private static AccessMutationMetadata metadata() {
    return new AccessMutationMetadata(
        12L, null, "test", "correlation-1", Instant.parse("2026-08-15T12:00:00Z"));
  }
}
