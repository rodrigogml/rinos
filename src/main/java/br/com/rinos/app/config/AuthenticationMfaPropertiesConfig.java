package br.com.rinos.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parâmetros funcionais fixos dos fatores adicionais.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@ConfigurationProperties("rinos.authentication.mfa")
public record AuthenticationMfaPropertiesConfig(
    @DefaultValue("5m") Duration challengeValidity,
    @DefaultValue("5") int maximumAttempts) {
  public AuthenticationMfaPropertiesConfig {
    if (challengeValidity == null || challengeValidity.isNegative() || challengeValidity.isZero())
      throw new IllegalArgumentException("challengeValidity deve ser maior que zero.");
    if (maximumAttempts <= 0)
      throw new IllegalArgumentException("maximumAttempts deve ser maior que zero.");
  }
}
