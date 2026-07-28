package br.com.rinos.app.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define a integração k-anônima com a API Pwned Passwords.
 *
 * @param endpoint endpoint base da consulta por prefixo SHA-1
 * @param userAgent identificação operacional enviada pela aplicação
 * @param connectTimeout timeout de conexão
 * @param readTimeout timeout de leitura
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.hibp")
public record PwnedPasswordsPropertiesConfig(
    @DefaultValue("https://api.pwnedpasswords.com/range/") URI endpoint,
    @DefaultValue("Rinos/1.0.0") String userAgent,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("5s") Duration readTimeout) {

  /**
   * Rejeita endpoint relativo, identificação vazia e timeouts sem duração.
   */
  public PwnedPasswordsPropertiesConfig {
    if (endpoint == null || !endpoint.isAbsolute()) {
      throw new IllegalArgumentException("endpoint deve ser uma URI absoluta.");
    }
    if (userAgent == null || userAgent.isBlank()) {
      throw new IllegalArgumentException("userAgent é obrigatório.");
    }
    if (!isPositive(connectTimeout) || !isPositive(readTimeout)) {
      throw new IllegalArgumentException("Os timeouts do HIBP devem ser maiores que zero.");
    }
  }

  private static boolean isPositive(Duration value) {
    return value != null && !value.isNegative() && !value.isZero();
  }
}
