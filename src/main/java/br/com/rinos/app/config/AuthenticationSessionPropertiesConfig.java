package br.com.rinos.app.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Políticas fixas das sessões globais e reautenticação.
 *
 * @author Rodrigo Leitão
 */
@ConfigurationProperties("rinos.authentication.session")
public record AuthenticationSessionPropertiesConfig(
    @DefaultValue("12h") Duration normalAbsolute,
    @DefaultValue("30m") Duration normalIdle,
    @DefaultValue("30d") Duration rememberedAbsolute,
    @DefaultValue("7d") Duration rememberedIdle,
    @DefaultValue("5m") Duration activityRefreshInterval,
    @DefaultValue("15m") Duration reauthenticationValidity,
    @DefaultValue("RINOS_AUTH") String cookieName,
    @DefaultValue("true") boolean cookieSecure,
    @DefaultValue("Strict") String cookieSameSite) {
  public AuthenticationSessionPropertiesConfig {
    positive(normalAbsolute, "normalAbsolute"); positive(normalIdle, "normalIdle");
    positive(rememberedAbsolute, "rememberedAbsolute"); positive(rememberedIdle, "rememberedIdle");
    positive(activityRefreshInterval, "activityRefreshInterval"); positive(reauthenticationValidity, "reauthenticationValidity");
    if (normalIdle.compareTo(normalAbsolute) > 0 || rememberedIdle.compareTo(rememberedAbsolute) > 0)
      throw new IllegalArgumentException("inatividade da sessão não pode exceder sua duração absoluta.");
    if (cookieName == null || !cookieName.matches("[A-Z][A-Z0-9_]{2,31}"))
      throw new IllegalArgumentException("cookieName deve ser um identificador seguro.");
    if (!"Strict".equals(cookieSameSite))
      throw new IllegalArgumentException("cookieSameSite deve permanecer Strict.");
  }
  private static void positive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " deve ser maior que zero.");
  }
}
