package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define os limites persistentes de prevenção de abuso por origem.
 *
 * @param turnstileThreshold quantidade aceita antes de exigir Turnstile
 * @param absoluteLimit quantidade máxima de novas pendências na janela
 * @param window duração da janela iniciada pela primeira criação contabilizada
 * @param retentionAfterWindow retenção máxima do IP depois do fim da janela
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.origin")
public record OriginPropertiesConfig(
    @DefaultValue("0") int turnstileThreshold,
    @DefaultValue("20") int absoluteLimit,
    @DefaultValue("24h") Duration window,
    @DefaultValue("30d") Duration retentionAfterWindow) {

  /**
   * Garante limites coerentes e prazos positivos.
   */
  public OriginPropertiesConfig {
    if (turnstileThreshold < 0) {
      throw new IllegalArgumentException("turnstileThreshold não pode ser negativo.");
    }
    if (absoluteLimit <= 0 || turnstileThreshold > absoluteLimit) {
      throw new IllegalArgumentException(
          "absoluteLimit deve ser positivo e não pode ser menor que turnstileThreshold.");
    }
    if (window == null || window.isNegative() || window.isZero()) {
      throw new IllegalArgumentException("window deve ser maior que zero.");
    }
    if (retentionAfterWindow == null || retentionAfterWindow.isNegative()) {
      throw new IllegalArgumentException("retentionAfterWindow não pode ser negativa.");
    }
  }
}
