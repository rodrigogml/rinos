package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;

@DisplayName("Política de garantia da autenticação")
class AuthenticationAssurancePolicyServiceTest {

  private static final Instant VERIFIED_AT = Instant.parse("2026-08-08T12:00:00Z");
  private final AuthenticationAssurancePolicyService service =
      new AuthenticationAssurancePolicyService();

  @Test
  void calculate_shouldClassifyOneMethodAsSingleFactor() {
    assertThat(service.calculate(List.of(method(AuthenticationMethodEnum.PASSWORD, null))))
        .isEqualTo(AuthenticationAssuranceEnum.SINGLE_FACTOR);
  }

  @Test
  void calculate_shouldClassifyIndependentMethodsAsMultiFactor() {
    assertThat(service.calculate(List.of(
        method(AuthenticationMethodEnum.PASSWORD, null),
        method(AuthenticationMethodEnum.TOTP, null))))
        .isEqualTo(AuthenticationAssuranceEnum.MULTI_FACTOR);
  }

  @Test
  void calculate_shouldNotCountGoogleAndEmailAsIndependentChannels() {
    assertThat(service.calculate(List.of(
        method(AuthenticationMethodEnum.GOOGLE, null),
        method(AuthenticationMethodEnum.EMAIL_CODE, null))))
        .isEqualTo(AuthenticationAssuranceEnum.SINGLE_FACTOR);
  }

  @Test
  void calculate_shouldClassifyUserVerifiedPasskeyAsPhishingResistant() {
    AuthenticationAssuranceEnum achieved = service.calculate(List.of(
        method(AuthenticationMethodEnum.PASSKEY, true)));

    assertThat(achieved).isEqualTo(AuthenticationAssuranceEnum.PHISHING_RESISTANT);
    assertThat(service.satisfies(
        achieved,
        AuthenticationAssuranceEnum.MULTI_FACTOR)).isTrue();
  }

  private static AuthenticationFlowVerifiedMethodVO method(
      AuthenticationMethodEnum method,
      Boolean userVerification) {
    return new AuthenticationFlowVerifiedMethodVO(method, VERIFIED_AT, userVerification);
  }
}
