package br.com.rinos.app.config;

import java.net.URI;
import java.util.Locale;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.PasskeyConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.PasskeyUserVerification;

/**
 * Reforça invariantes fixas do Rinos sobre protocolos configuráveis fornecidos pela RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationProtocolPropertiesValidatorConfig {

  /**
   * Falha a inicialização quando um protocolo diverge das invariantes do Rinos.
   *
   * @param authentication propriedades de autenticação fornecidas pela RFW
   * @return validação executada depois da criação dos singletons
   */
  @Bean
  SmartInitializingSingleton authenticationProtocolPropertiesValidator(
      RFWAuthenticationPropertiesConfig authentication) {
    return () -> {
      if (authentication.secondFactor().recoveryCodeCount() != 10) {
        throw new IllegalStateException(
            "O Rinos exige exatamente 10 códigos de recuperação.");
      }
      validatePasskey(authentication.passkey());
    };
  }

  private static void validatePasskey(PasskeyConfig passkey) {
    if (!passkey.enabled()) {
      return;
    }
    if (isBlank(passkey.relyingPartyId()) || isBlank(passkey.relyingPartyName())
        || passkey.allowedOrigins().isEmpty()) {
      throw new IllegalStateException(
          "Passkeys habilitadas exigem RP ID, nome do RP e ao menos uma origin.");
    }
    if (passkey.userVerification() != PasskeyUserVerification.REQUIRED) {
      throw new IllegalStateException(
          "O Rinos exige user-verification=required para passkeys.");
    }
    String relyingPartyId = passkey.relyingPartyId().toLowerCase(Locale.ROOT);
    if (!isValidRelyingPartyId(relyingPartyId)) {
      throw new IllegalStateException("RP ID WebAuthn inválido.");
    }
    passkey.allowedOrigins().forEach(origin -> validateOrigin(origin, relyingPartyId));
  }

  private static void validateOrigin(String configuredOrigin, String relyingPartyId) {
    if (isBlank(configuredOrigin)) {
      throw new IllegalStateException("Origin WebAuthn inválida.");
    }
    URI origin;
    try {
      origin = URI.create(configuredOrigin);
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Origin WebAuthn inválida.", exception);
    }
    String host = origin.getHost();
    boolean local = "localhost".equalsIgnoreCase(host);
    boolean validScheme = "https".equalsIgnoreCase(origin.getScheme())
        || (local && "http".equalsIgnoreCase(origin.getScheme()));
    boolean rootPath = origin.getPath() == null || origin.getPath().isEmpty()
        || "/".equals(origin.getPath());
    if (host == null || !validScheme || !rootPath || origin.getUserInfo() != null
        || origin.getQuery() != null || origin.getFragment() != null) {
      throw new IllegalStateException(
          "Origin WebAuthn deve ser HTTPS ou localhost HTTP, sem caminho ou extras.");
    }
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (!normalizedHost.equals(relyingPartyId)
        && !normalizedHost.endsWith("." + relyingPartyId)) {
      throw new IllegalStateException(
          "Toda origin WebAuthn deve pertencer ao domínio do RP ID configurado.");
    }
  }

  private static boolean isValidRelyingPartyId(String relyingPartyId) {
    return "localhost".equals(relyingPartyId)
        || relyingPartyId.matches(
            "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
