package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusTransitionEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionRevocationReasonEnum;
import br.com.rinos.app.backend.module.identity.vo.IdentityTransitionVO;

@DisplayName("Lifecycle da identidade global")
class UserLifecycleServiceTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T18:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("97920335-dd61-432b-b67d-0861e319362b");

  private final UserLifecycleService service = new UserLifecycleService();

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

  private static UserEntity user(UserStatusEnum status) {
    return new UserEntity("user@example.com", "user@example.com", status);
  }
}
