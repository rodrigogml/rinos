package br.com.rinos.app.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
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

  private static SmartInitializingSingleton validator(int recoveryCodeCount) {
    SecondFactorConfig secondFactor = new SecondFactorConfig(
        6, 30, 1, 6, recoveryCodeCount);
    RFWAuthenticationPropertiesConfig properties =
        new RFWAuthenticationPropertiesConfig(null, null, null, secondFactor);
    return new AuthenticationProtocolPropertiesValidatorConfig()
        .authenticationProtocolPropertiesValidator(properties);
  }
}
