package br.com.rinos.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.mail.autoconfigure.MailProperties;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.TurnstileConfig;
import br.eng.rodrigogml.rfw.mail.config.EmailTemplatePropertiesConfig;

@DisplayName("Validação das propriedades de integrações externas")
class ExternalIntegrationPropertiesValidatorConfigTest {

  @Test
  void validator_shouldRejectStartup_whenEnabledTurnstileHasNoSecret() {
    TurnstileConfig turnstile = new TurnstileConfig(
        true,
        "test-site",
        null,
        List.of("rinos.test"),
        Duration.ofSeconds(1));
    RFWAuthenticationPropertiesConfig authentication =
        new RFWAuthenticationPropertiesConfig(null, null, turnstile, null);
    SmartInitializingSingleton validator = new ExternalIntegrationPropertiesValidatorConfig()
        .externalIntegrationPropertiesValidator(
            authentication,
            new MailProperties(),
            new EmailTemplatePropertiesConfig());

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, validator::afterSingletonsInstantiated);

    assertEquals(
        "Turnstile habilitado exige siteKey, secretKey e expectedHostnames.",
        exception.getMessage());
  }
}
