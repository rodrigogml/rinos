package br.com.rinos.app.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.PasskeyConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.PasskeyUserVerification;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.SecondFactorConfig;

@DisplayName("Invariantes dos protocolos de autenticação")
class AuthenticationProtocolPropertiesValidatorConfigTest {

  @Test
  void validator_shouldAcceptStartup_whenRecoveryCodeCountIsTen() {
    SmartInitializingSingleton validator = validator(10);

    assertDoesNotThrow(validator::afterSingletonsInstantiated);
  }

  @Test
  void validator_shouldRejectStartup_whenRecoveryCodeCountDiffersFromContract() {
    SmartInitializingSingleton validator = validator(9);

    IllegalStateException exception = assertThrows(
        IllegalStateException.class, validator::afterSingletonsInstantiated);

    assertEquals("O Rinos exige exatamente 10 códigos de recuperação.", exception.getMessage());
  }

  @Test
  void validator_shouldAcceptRequiredVerification_withMatchingLocalOrigin() {
    PasskeyConfig passkey = passkey(
        "localhost", "http://localhost:7070", PasskeyUserVerification.REQUIRED);

    assertDoesNotThrow(validator(10, passkey)::afterSingletonsInstantiated);
  }

  @Test
  void validator_shouldRejectStartup_whenPasskeyVerificationIsNotRequired() {
    PasskeyConfig passkey = passkey(
        "app.rinos.com.br", "https://app.rinos.com.br", PasskeyUserVerification.PREFERRED);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        validator(10, passkey)::afterSingletonsInstantiated);

    assertEquals("O Rinos exige user-verification=required para passkeys.", exception.getMessage());
  }

  @Test
  void validator_shouldRejectStartup_whenOriginDoesNotBelongToRelyingParty() {
    PasskeyConfig passkey = passkey(
        "app.rinos.com.br", "https://example.com", PasskeyUserVerification.REQUIRED);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        validator(10, passkey)::afterSingletonsInstantiated);

    assertEquals("Toda origin WebAuthn deve pertencer ao domínio do RP ID configurado.",
        exception.getMessage());
  }

  @Test
  void validator_shouldRejectStartup_whenRelyingPartyContainsAUrl() {
    PasskeyConfig passkey = passkey(
        "https://app.rinos.com.br", "https://app.rinos.com.br",
        PasskeyUserVerification.REQUIRED);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        validator(10, passkey)::afterSingletonsInstantiated);

    assertEquals("RP ID WebAuthn inválido.", exception.getMessage());
  }

  @Test
  void validator_shouldRejectStartup_whenRemoteOriginUsesHttp() {
    PasskeyConfig passkey = passkey(
        "app.rinos.com.br", "http://app.rinos.com.br", PasskeyUserVerification.REQUIRED);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        validator(10, passkey)::afterSingletonsInstantiated);

    assertEquals("Origin WebAuthn deve ser HTTPS ou localhost HTTP, sem caminho ou extras.",
        exception.getMessage());
  }

  private static SmartInitializingSingleton validator(int recoveryCodeCount) {
    return validator(recoveryCodeCount, null);
  }

  private static SmartInitializingSingleton validator(int recoveryCodeCount,
      PasskeyConfig passkey) {
    SecondFactorConfig secondFactor = new SecondFactorConfig(
        6, 30, 1, 6, recoveryCodeCount);
    RFWAuthenticationPropertiesConfig properties =
        new RFWAuthenticationPropertiesConfig(null, passkey, null, secondFactor);
    return new AuthenticationProtocolPropertiesValidatorConfig()
        .authenticationProtocolPropertiesValidator(properties);
  }

  private static PasskeyConfig passkey(String relyingPartyId, String origin,
      PasskeyUserVerification userVerification) {
    return new PasskeyConfig(
        true, relyingPartyId, "Rinos", List.of(origin), userVerification);
  }
}
