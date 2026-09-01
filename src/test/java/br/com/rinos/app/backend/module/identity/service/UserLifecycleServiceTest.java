package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.module.plans.dto.PersonalContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusTransitionEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionRevocationReasonEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;

@DisplayName("Lifecycle da identidade global")
class UserLifecycleServiceTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T18:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("97920335-dd61-432b-b67d-0861e319362b");

  private final UserLifecycleService service = lifecycleWithPersonalContract();

  /**
   * Comprova cada par de estados publicado no catálogo.
   */
  @Test
  void transition_shouldApplyEveryCataloguedPair_whenTransitionIsAllowed() {
    for (UserStatusTransitionEnum transition : UserStatusTransitionEnum.values()) {
      UserEntity user = user(transition.getPreviousStatus());

      IdentityTransitionVO result = service.transition(
          user,
          transition.getNewStatus(),
          IdentityTransitionOriginEnum.SYSTEM,
          "test-transition",
          OCCURRED_AT);

      assertThat(user.getStatus()).isEqualTo(transition.getNewStatus());
      assertThat(result.previousStatus()).isEqualTo(transition.getPreviousStatus().name());
      assertThat(result.newStatus()).isEqualTo(transition.getNewStatus().name());
    }
  }

  /**
   * Registra o instante somente na primeira ativação do cadastro.
   */
  @Test
  void transition_shouldSetActivatedAt_whenPendingUserBecomesActive() {
    UserEntity user = user(UserStatusEnum.PENDING_VERIFICATION);

    IdentityTransitionVO result = service.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        null,
        OCCURRED_AT);

    assertThat(user.getActivatedAt()).isEqualTo(OCCURRED_AT);
    assertThat(result.entityType()).isEqualTo("USER");
    assertThat(result.origin()).isEqualTo(IdentityTransitionOriginEnum.SELF_SERVICE);
  }

  @Test
  void transition_shouldEnsurePersonalContractWithStableActivationIntent_whenPendingUserBecomesActive() {
    PersonalContractBootstrapPort contracts = mock(PersonalContractBootstrapPort.class);
    when(contracts.ensure(any())).thenReturn(new ContractBootstrapResult(
        ContractBootstrapStatus.COMPLETED,
        ContractScope.PERSONAL,
        UUID.fromString("c58ee213-d786-4c42-8802-56f5d9fd7a35"),
        null));
    UserLifecycleService lifecycle = new UserLifecycleService(mock(AuthSessionService.class), contracts);
    UserEntity user = user(UserStatusEnum.PENDING_VERIFICATION);

    lifecycle.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED",
        OCCURRED_AT,
        CORRELATION_ID);

    ArgumentCaptor<PersonalContractBootstrapRequest> request =
        ArgumentCaptor.forClass(PersonalContractBootstrapRequest.class);
    verify(contracts).ensure(request.capture());
    assertThat(request.getValue().protocolId()).isEqualTo(CORRELATION_ID);
    assertThat(request.getValue().userId()).isEqualTo(42L);
    assertThat(request.getValue().correlationId()).isEqualTo(CORRELATION_ID.toString());
    assertThat(user.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
  }

  @Test
  void transition_shouldKeepUserPending_whenPersonalContractServiceIsUnavailable() {
    UserEntity user = user(UserStatusEnum.PENDING_VERIFICATION);
    UserLifecycleService unavailableLifecycle = new UserLifecycleService();

    assertThatThrownBy(() -> unavailableLifecycle.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED",
        OCCURRED_AT,
        CORRELATION_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("personal plan contract service is unavailable");
    assertThat(user.getStatus()).isEqualTo(UserStatusEnum.PENDING_VERIFICATION);
  }

  @Test
  void transition_shouldKeepUserPending_whenPersonalContractCannotBeEnsured() {
    UserEntity user = user(UserStatusEnum.PENDING_VERIFICATION);
    PersonalContractBootstrapPort unavailableContracts = request -> new ContractBootstrapResult(
        ContractBootstrapStatus.UNAVAILABLE,
        ContractScope.PERSONAL,
        null,
        "CATALOG_UNAVAILABLE");
    UserLifecycleService unavailableLifecycle = new UserLifecycleService(
        mock(AuthSessionService.class), unavailableContracts);

    assertThatThrownBy(() -> unavailableLifecycle.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED",
        OCCURRED_AT,
        CORRELATION_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("personal plan contract could not be ensured");
    assertThat(user.getStatus()).isEqualTo(UserStatusEnum.PENDING_VERIFICATION);
  }

  private static UserLifecycleService lifecycleWithPersonalContract() {
    AuthSessionService sessions = mock(AuthSessionService.class);
    PersonalContractBootstrapPort contracts = request -> new ContractBootstrapResult(
        ContractBootstrapStatus.ALREADY_COMPLETED,
        ContractScope.PERSONAL,
        UUID.randomUUID(),
        null);
    return new UserLifecycleService(sessions, contracts);
  }

  /**
   * Bloqueia pares que não pertencem ao contrato de estados.
   */
  @Test
  void transition_shouldRejectChange_whenPairIsNotAllowed() {
    UserEntity user = user(UserStatusEnum.CANCELLED);

    assertThatThrownBy(() -> service.transition(
        user,
        UserStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SYSTEM,
        null,
        OCCURRED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("User transition is not allowed: CANCELLED -> ACTIVE");
    assertThat(user.getStatus()).isEqualTo(UserStatusEnum.CANCELLED);
  }

  @Test
  void transition_shouldRevokeEverySession_whenActiveUserIsBlocked() {
    AuthSessionService sessions = mock(AuthSessionService.class);
    UserLifecycleService operationalService = new UserLifecycleService(sessions);
    UserEntity user = user(UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);

    operationalService.transition(
        user,
        UserStatusEnum.BLOCKED,
        IdentityTransitionOriginEnum.SYSTEM,
        "RISK",
        OCCURRED_AT,
        CORRELATION_ID);

    verify(sessions).revokeAll(
        41L,
        null,
        AuthSessionRevocationReasonEnum.SECURITY_EVENT,
        OCCURRED_AT,
        CORRELATION_ID);
  }

  @Test
  void transition_shouldLockContextsFlushAndReviseBeforeRevokingSessions_whenActiveIdentityIsBlocked() {
    AuthSessionService sessions = mock(AuthSessionService.class);
    PersonalContractBootstrapPort contracts = mock(PersonalContractBootstrapPort.class);
    UserRepository users = mock(UserRepository.class);
    AdministrativeIdentityContinuityPort continuity = mock(AdministrativeIdentityContinuityPort.class);
    UserEntity user = user(UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    AdministrativeIdentityContinuityContext context =
        new AdministrativeIdentityContinuityContext(41L, java.util.List.of(8L));
    when(continuity.lockIdentityContexts(41L)).thenReturn(context);
    when(users.findByIdForUpdate(41L)).thenReturn(java.util.Optional.of(user));
    UserLifecycleService operationalService = new UserLifecycleService(
        sessions, contracts, users, continuity);

    operationalService.transition(
        user,
        UserStatusEnum.BLOCKED,
        IdentityTransitionOriginEnum.SYSTEM,
        "RISK",
        OCCURRED_AT,
        CORRELATION_ID);

    InOrder order = inOrder(continuity, users, sessions);
    order.verify(continuity).lockIdentityContexts(41L);
    order.verify(users).findByIdForUpdate(41L);
    order.verify(users).flush();
    order.verify(continuity).validateAndRevise(context, OCCURRED_AT);
    order.verify(sessions).revokeAll(
        41L, null, AuthSessionRevocationReasonEnum.SECURITY_EVENT, OCCURRED_AT, CORRELATION_ID);
  }

  @Test
  void transition_shouldAbortBeforeSessionRevocation_whenIdentityWouldRemoveLastAdministrator() {
    AuthSessionService sessions = mock(AuthSessionService.class);
    PersonalContractBootstrapPort contracts = mock(PersonalContractBootstrapPort.class);
    UserRepository users = mock(UserRepository.class);
    AdministrativeIdentityContinuityPort continuity = mock(AdministrativeIdentityContinuityPort.class);
    UserEntity user = user(UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    AdministrativeIdentityContinuityContext context =
        new AdministrativeIdentityContinuityContext(41L, java.util.List.of());
    when(continuity.lockIdentityContexts(41L)).thenReturn(context);
    when(users.findByIdForUpdate(41L)).thenReturn(java.util.Optional.of(user));
    org.mockito.Mockito.doThrow(new IllegalArgumentException("administrative continuity would be lost"))
        .when(continuity).validateAndRevise(context, OCCURRED_AT);
    UserLifecycleService operationalService = new UserLifecycleService(
        sessions, contracts, users, continuity);

    assertThatThrownBy(() -> operationalService.transition(
        user,
        UserStatusEnum.BLOCKED,
        IdentityTransitionOriginEnum.SYSTEM,
        "RISK",
        OCCURRED_AT,
        CORRELATION_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("continuity would be lost");

    verify(users).flush();
    verifyNoInteractions(sessions);
  }

  private static UserEntity user(UserStatusEnum status) {
    UserEntity user = new UserEntity("user@example.com", "user@example.com", status);
    ReflectionTestUtils.setField(user, "id", 42L);
    return user;
  }
}
