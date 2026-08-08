package br.com.rinos.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parâmetros funcionais fixos dos fatores adicionais.
 *
 * @author Rodrigo Leitão
 */
@ConfigurationProperties("rinos.authentication.mfa")
public record AuthenticationMfaPropertiesConfig(
    @DefaultValue("5m") Duration challengeValidity,
    @DefaultValue("5") int maximumAttempts,
    @DefaultValue("6") int totpDigits,
    @DefaultValue("30s") Duration totpPeriod,
    @DefaultValue("1") int totpWindow,
    @DefaultValue("10") int recoveryCodeCount) {
  public AuthenticationMfaPropertiesConfig {
    if (challengeValidity == null || challengeValidity.isNegative() || challengeValidity.isZero())
      throw new IllegalArgumentException("challengeValidity deve ser maior que zero.");
    if (maximumAttempts <= 0 || totpDigits != 6 || !Duration.ofSeconds(30).equals(totpPeriod)
        || totpWindow != 1 || recoveryCodeCount != 10)
      throw new IllegalArgumentException("parâmetros MFA divergem da política aprovada.");
  }
}
