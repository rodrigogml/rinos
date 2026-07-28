package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define frequência e tamanho dos lotes das rotinas globais de limpeza.
 *
 * @param interval intervalo máximo entre execuções
 * @param batchSize quantidade máxima de registros por transação
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.cleanup")
public record CleanupPropertiesConfig(
    @DefaultValue("24h") Duration interval,
    @DefaultValue("500") int batchSize) {

  /**
   * Impede intervalo ou lote sem capacidade de execução.
   */
  public CleanupPropertiesConfig {
    if (interval == null || interval.isNegative() || interval.isZero()) {
      throw new IllegalArgumentException("interval deve ser maior que zero.");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize deve ser maior que zero.");
    }
  }
}
