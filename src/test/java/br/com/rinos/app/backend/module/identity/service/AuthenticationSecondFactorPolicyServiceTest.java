package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;

@DisplayName("Política contextual de segundo fator")
class AuthenticationSecondFactorPolicyServiceTest {

  private final AuthenticationSecondFactorPolicyService service =
      new AuthenticationSecondFactorPolicyService();

  @Test
  void permittedMethods_shouldOfferImplementedFactorsAfterPassword() {
    Set<AuthenticationMethodEnum> result = service.permittedMethods(
        AuthenticationMethodEnum.PASSWORD,
        Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.PASSKEY,
            AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.EMAIL_CODE,
            AuthenticationMethodEnum.RECOVERY_CODE));

    assertThat(result).containsExactlyInAnyOrder(
        AuthenticationMethodEnum.TOTP,
        AuthenticationMethodEnum.EMAIL_CODE,
        AuthenticationMethodEnum.RECOVERY_CODE);
  }

  @Test
  void permittedMethods_shouldRejectSameEmailChannelAfterGoogle() {
    Set<AuthenticationMethodEnum> result = service.permittedMethods(
        AuthenticationMethodEnum.GOOGLE,
        Set.of(AuthenticationMethodEnum.GOOGLE, AuthenticationMethodEnum.TOTP,
            AuthenticationMethodEnum.EMAIL_CODE, AuthenticationMethodEnum.RECOVERY_CODE));

    assertThat(result).containsExactlyInAnyOrder(
        AuthenticationMethodEnum.TOTP,
        AuthenticationMethodEnum.RECOVERY_CODE);
  }

  @Test
  void requiresMultiFactor_shouldIgnoreUnavailableFuturePasskeyVerifier() {
    assertThat(service.requiresMultiFactor(Set.of(AuthenticationMethodEnum.PASSKEY))).isFalse();
    assertThat(service.requiresMultiFactor(Set.of(AuthenticationMethodEnum.EMAIL_CODE))).isTrue();
  }
}
