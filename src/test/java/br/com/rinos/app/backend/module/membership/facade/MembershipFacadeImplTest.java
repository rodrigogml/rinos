package br.com.rinos.app.backend.module.membership.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.InitialModuleAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipInvitationRequest;
import br.com.rinos.app.api.module.membership.dto.MembershipInvocationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipMutationRequest;
import br.com.rinos.app.api.module.membership.enums.MembershipInvitationResultStatus;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;
import br.com.rinos.app.api.module.membership.vo.MembershipInvitationResult;
import br.com.rinos.app.backend.module.membership.service.MembershipInvitationService;
import br.com.rinos.app.backend.module.membership.service.MembershipLifecycleService;
import br.com.rinos.app.backend.module.membership.service.MembershipMutationCommand;

class MembershipFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-16T16:00:00Z");

  @Test
  void issue_shouldNotReachPersistenceWhenPermissionIsMissingOrBlocked() {
    Fixture fixture = new Fixture();
    when(fixture.authorization.require(any())).thenThrow(new IllegalStateException("ACL_DENIED"));

    assertThatThrownBy(() -> fixture.facade.issue(context(7L, 44L),
        new MembershipInvitationRequest(
            UUID.randomUUID(), "person@example.test", MembershipRoleType.ACCOUNT_ADMINISTRATOR)))
        .isInstanceOf(IllegalStateException.class).hasMessage("ACL_DENIED");

    verifyNoInteractions(fixture.invitations, fixture.lifecycle);
  }

  @Test
  void issue_shouldAuthorizeCanonicalInviteKeyInExactTenantRegardlessOfProposedRole() {
    Fixture fixture = new Fixture();
    UUID account = UUID.randomUUID();
    var result = new MembershipInvitationResult(
        MembershipInvitationResultStatus.ISSUED, UUID.randomUUID(), "proof", NOW.plusSeconds(60), null);
    when(fixture.invitations.issue(any(Long.class), any(UUID.class), any(String.class),
        any(MembershipRoleType.class), any(String.class), any(String.class), any(Instant.class)))
        .thenReturn(result);

    assertThat(fixture.facade.issue(context(7L, 44L), new MembershipInvitationRequest(
        account, "person@example.test", MembershipRoleType.COLLABORATOR))).isSameAs(result);

    ArgumentCaptor<AuthorizationRequest> authorization = ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(fixture.authorization).require(authorization.capture());
    assertThat(authorization.getValue().context().tenantId()).isEqualTo(44L);
    assertThat(authorization.getValue().membershipId()).isEqualTo(7L);
    assertThat(authorization.getValue().requiredKeys())
        .containsExactly(InitialModuleAccessKeys.TENANT_MEMBERSHIP_INVITE);
    assertThat(authorization.getValue().sensitive()).isFalse();
  }

  @Test
  void mutate_shouldRequireSensitiveManageKeyBeforeBuildingTrustedCommand() {
    Fixture fixture = new Fixture();
    UUID target = UUID.randomUUID();
    var request = new MembershipMutationRequest(
        target, MembershipMutationOperation.SUSPEND, null, 3L, true);

    fixture.facade.mutate(context(8L, 55L), request);

    ArgumentCaptor<AuthorizationRequest> authorization = ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(fixture.authorization).require(authorization.capture());
    assertThat(authorization.getValue().requiredKeys())
        .containsExactly(InitialModuleAccessKeys.TENANT_MEMBERSHIP_MANAGE);
    assertThat(authorization.getValue().sensitive()).isTrue();
    ArgumentCaptor<MembershipMutationCommand> command =
        ArgumentCaptor.forClass(MembershipMutationCommand.class);
    verify(fixture.lifecycle).mutate(command.capture());
    assertThat(command.getValue().actorMembershipId()).isEqualTo(8L);
    assertThat(command.getValue().recentStrongAuthentication()).isTrue();
    assertThat(command.getValue().targetMembershipPublicId()).isEqualTo(target);
  }

  private static MembershipInvocationContext context(long membershipId, long tenantId) {
    return new MembershipInvocationContext(
        AuthorizationActor.human(99L), membershipId, AuthorizationContext.tenant(tenantId),
        new AuthenticationAssurance(AuthenticationAssuranceEnum.MULTI_FACTOR,
            Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP),
            NOW.minusSeconds(600), NOW.minusSeconds(30)),
        "correlation-test", NOW);
  }

  private static final class Fixture {
    private final AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    private final MembershipInvitationService invitations = mock(MembershipInvitationService.class);
    private final MembershipLifecycleService lifecycle = mock(MembershipLifecycleService.class);
    private final MembershipFacadeImpl facade =
        new MembershipFacadeImpl(authorization, invitations, lifecycle);
  }
}
