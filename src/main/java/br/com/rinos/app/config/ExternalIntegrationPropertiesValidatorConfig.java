package br.com.rinos.app.config;

import java.net.URI;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.GoogleConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.TurnstileConfig;
import br.eng.rodrigogml.rfw.mail.config.EmailTemplatePropertiesConfig;

/**
 * Valida conjuntamente configurações externas já tipadas pelo Spring Boot e pelo RFW Platform.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@Configuration(proxyBeanMethods = false)
public class ExternalIntegrationPropertiesValidatorConfig {

  /**
   * Falha a inicialização quando uma integração habilitada está incompleta ou incompatível.
   *
   * @param authentication propriedades públicas de autenticação do RFW
   * @param mail propriedades SMTP do Spring Boot
   * @param templates propriedades de remetente e templates do RFW
   * @return validação executada depois da criação dos singletons
   */
  @Bean
  SmartInitializingSingleton externalIntegrationPropertiesValidator(
      RFWAuthenticationPropertiesConfig authentication,
      MailProperties mail,
      EmailTemplatePropertiesConfig templates) {
    return () -> {
      validateTurnstile(authentication.turnstile());
      validateGoogle(authentication.google());
      validateMail(mail, templates);
    };
  }

  private static void validateTurnstile(TurnstileConfig turnstile) {
    if (!turnstile.enabled()) {
      return;
    }
    if (isBlank(turnstile.siteKey()) || isBlank(turnstile.secretKey())
        || turnstile.expectedHostnames().isEmpty()) {
      throw new IllegalStateException(
          "Turnstile habilitado exige siteKey, secretKey e expectedHostnames.");
    }
    if (turnstile.timeout().isNegative() || turnstile.timeout().isZero()) {
      throw new IllegalStateException("O timeout do Turnstile deve ser maior que zero.");
    }
  }

  private static void validateGoogle(GoogleConfig google) {
    if (!google.enabled()) {
      return;
    }
    if (isBlank(google.clientId())) {
      throw new IllegalStateException("Google habilitado exige clientId.");
    }
    URI issuer = URI.create(google.issuer());
    if (!issuer.isAbsolute() || !"https".equalsIgnoreCase(issuer.getScheme())) {
      throw new IllegalStateException("O issuer do Google deve ser uma URI HTTPS absoluta.");
    }
  }

  private static void validateMail(MailProperties mail, EmailTemplatePropertiesConfig templates) {
    if (isBlank(mail.getHost())) {
      throw new IllegalStateException("spring.mail.host é obrigatório.");
    }
    Integer port = mail.getPort();
    if (port == null || port <= 0 || port > 65_535) {
      throw new IllegalStateException("spring.mail.port deve estar entre 1 e 65535.");
    }
    if (isBlank(templates.getDefaultFromAddress())) {
      throw new IllegalStateException("rfw.mail.default-from-address é obrigatório.");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
