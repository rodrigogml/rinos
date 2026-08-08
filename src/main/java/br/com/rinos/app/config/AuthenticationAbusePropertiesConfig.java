package br.com.rinos.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Política progressiva por identificador normalizado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@ConfigurationProperties("rinos.authentication.abuse")
public record AuthenticationAbusePropertiesConfig(
    @DefaultValue("3") int failureThreshold,
    @DefaultValue("15m") Duration window,
    @DefaultValue("15m") Duration turnstileDuration,
    @DefaultValue("1s") Duration initialDelay,
    @DefaultValue("15m") Duration maximumDelay,
    @DefaultValue("30d") Duration retention) {
  public AuthenticationAbusePropertiesConfig {
    if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold deve ser positivo.");
    positive(window, "window"); positive(turnstileDuration, "turnstileDuration");
    positive(initialDelay, "initialDelay"); positive(maximumDelay, "maximumDelay"); positive(retention, "retention");
    if (initialDelay.compareTo(maximumDelay) > 0) throw new IllegalArgumentException("initialDelay não pode exceder maximumDelay.");
  }
  private static void positive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " deve ser maior que zero.");
  }
}
