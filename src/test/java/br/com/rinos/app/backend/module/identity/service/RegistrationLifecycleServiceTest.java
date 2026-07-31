package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusTransitionEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;

@DisplayName("Lifecycle do cadastro temporário")
class RegistrationLifecycleServiceTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T18:00:00Z");

  private final RegistrationLifecycleService service = new RegistrationLifecycleService();

  /**
   * Comprova cada transição terminal publicada no catálogo.
   */
  @Test
  void transition_shouldApplyEveryCataloguedPair_whenTransitionIsAllowed() {
    for (RegistrationStatusTransitionEnum transition
        : RegistrationStatusTransitionEnum.values()) {
      RegistrationEntity registration = registration(transition.getPreviousStatus());

      service.transition(
          registration,
          transition.getNewStatus(),
          IdentityTransitionOriginEnum.SYSTEM,
          "test-transition",
          OCCURRED_AT);

      assertThat(registration.getStatus()).isEqualTo(transition.getNewStatus());
    }
  }

  /**
   * Registra a conclusão quando a pendência é ativada.
   */
  @Test
  void transition_shouldSetCompletedAt_whenRegistrationBecomesActive() {
    RegistrationEntity registration =
        registration(RegistrationStatusEnum.PENDING_VERIFICATION);

    service.transition(
        registration,
        RegistrationStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        null,
        OCCURRED_AT);

    assertThat(registration.getCompletedAt()).isEqualTo(OCCURRED_AT);
    assertThat(registration.getCancelledAt()).isNull();
  }

  /**
   * Registra o instante quando a pendência é cancelada.
   */
  @Test
  void transition_shouldSetCancelledAt_whenRegistrationIsCancelled() {
    RegistrationEntity registration =
        registration(RegistrationStatusEnum.PENDING_VERIFICATION);

    service.transition(
        registration,
        RegistrationStatusEnum.CANCELLED,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "requested-by-owner",
        OCCURRED_AT);

    assertThat(registration.getCancelledAt()).isEqualTo(OCCURRED_AT);
    assertThat(registration.getCompletedAt()).isNull();
  }

  /**
   * Impede qualquer nova transição depois de um estado terminal.
   */
  @Test
  void transition_shouldRejectChange_whenRegistrationIsTerminal() {
    RegistrationEntity registration = registration(RegistrationStatusEnum.EXPIRED);

    assertThatThrownBy(() -> service.transition(
        registration,
        RegistrationStatusEnum.ACTIVE,
        IdentityTransitionOriginEnum.SYSTEM,
        null,
        OCCURRED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Registration transition is not allowed: EXPIRED -> ACTIVE");
    assertThat(registration.getStatus()).isEqualTo(RegistrationStatusEnum.EXPIRED);
  }

  private static RegistrationEntity registration(RegistrationStatusEnum status) {
    UserEntity user = new UserEntity(
        "user@example.com",
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    return new RegistrationEntity(
        user,
        RegistrationMethodEnum.LOCAL,
        status,
        OCCURRED_AT.plusSeconds(86_400));
  }
}
