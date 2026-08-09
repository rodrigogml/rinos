package br.com.rinos.app.backend.module.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;

@DisplayName("Janela deslizante de tentativas de autenticação")
class AuthenticationWindowEntityTest {

  @Test
  void registerFailure_shouldExtendWindowFromLatestFailure() {
    Instant startedAt = Instant.parse("2026-08-09T12:00:00Z");
    AuthenticationWindowEntity window = new AuthenticationWindowEntity(
        new byte[32],
        "v1",
        AuthenticationWindowOperationEnum.SIGN_IN,
        startedAt,
        startedAt.plusSeconds(900));

    window.registerFailure(3, startedAt.plusSeconds(1_200));

    assertThat(window.getFailureCount()).isEqualTo(1);
    assertThat(window.getWindowEndsAt()).isEqualTo(startedAt.plusSeconds(1_200));
    assertThat(window.getTurnstileRequiredUntil()).isNull();
  }
}
