package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define a validade das comprovações efêmeras emitidas pelo Rinos.
 *
 * @param validity validade de uma comprovação nova
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.verification")
public record VerificationPropertiesConfig(@DefaultValue("24h") Duration validity) {

  /**
   * Impede comprovações sem prazo de expiração positivo.
   */
  public VerificationPropertiesConfig {
    if (validity == null || validity.isNegative() || validity.isZero()) {
      throw new IllegalArgumentException("validity deve ser maior que zero.");
    }
  }
}
