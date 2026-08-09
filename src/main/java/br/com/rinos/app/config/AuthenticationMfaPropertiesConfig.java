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
    @DefaultValue("5") int maximumAttempts,
    @DefaultValue("1m") Duration emailResendCooldown,
    @DefaultValue("3") int emailEmissionLimit,
    @DefaultValue("15m") Duration emailEmissionWindow) {
  public AuthenticationMfaPropertiesConfig {
    if (challengeValidity == null || challengeValidity.isNegative() || challengeValidity.isZero())
      throw new IllegalArgumentException("challengeValidity deve ser maior que zero.");
    if (maximumAttempts <= 0)
      throw new IllegalArgumentException("maximumAttempts deve ser maior que zero.");
    if (emailResendCooldown == null || emailResendCooldown.isNegative()
        || emailResendCooldown.isZero())
      throw new IllegalArgumentException("emailResendCooldown deve ser maior que zero.");
    if (emailEmissionLimit <= 0)
      throw new IllegalArgumentException("emailEmissionLimit deve ser maior que zero.");
    if (emailEmissionWindow == null || emailEmissionWindow.isNegative()
        || emailEmissionWindow.isZero())
      throw new IllegalArgumentException("emailEmissionWindow deve ser maior que zero.");
    if (emailResendCooldown.compareTo(emailEmissionWindow) > 0)
      throw new IllegalArgumentException("emailResendCooldown não pode exceder emailEmissionWindow.");
  }
}
