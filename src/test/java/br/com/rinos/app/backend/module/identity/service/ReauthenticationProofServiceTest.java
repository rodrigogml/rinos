package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;

@DisplayName("Catálogo de provas reais de reautenticação")
class ReauthenticationProofServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private PasswordCredentialAuthenticationService passwordAuthentication;
  private ReauthenticationProofService service;

  @BeforeEach
  void setUp() {
    passwordAuthentication = mock(PasswordCredentialAuthenticationService.class);
    service = new ReauthenticationProofService(passwordAuthentication);
  }

  @Test
  void supportedMethods_shouldAdvertiseOnlyImplementedVerifier() {
    assertThat(service.supportedMethods()).containsExactly(AuthenticationMethodEnum.PASSWORD);
  }

  @Test
  void verify_shouldReturnSanitizedEvidenceAfterValidPassword() {
    when(passwordAuthentication.verifyUser(eq(41L), any(char[].class), eq(NOW)))
        .thenReturn(true);

    var result = service.verify(
        41L, AuthenticationMethodEnum.PASSWORD, "CorrectPassword1!", NOW);

    assertThat(result).hasValueSatisfying(proof -> {
      assertThat(proof.method()).isEqualTo(AuthenticationMethodEnum.PASSWORD);
      assertThat(proof.userVerification()).isNull();
    });
    verify(passwordAuthentication).verifyUser(eq(41L), any(char[].class), eq(NOW));
  }

  @Test
  void verify_shouldRejectMethodWithoutVerifierWithoutTouchingPasswordService() {
    assertThat(service.verify(
        41L, AuthenticationMethodEnum.TOTP, "123456", NOW)).isEmpty();

    verify(passwordAuthentication, never()).verifyUser(any(), any(), any());
  }
}
