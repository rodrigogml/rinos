package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define os limites fixos de proteção, repetição e processamento assíncrono do cadastro de
 * contas.
 *
 * <p>Os valores pertencem à operação da aplicação e são obtidos exclusivamente do arquivo raiz
 * {@code application.properties}; eles não são configurações editáveis por tenant nem pelo
 * administrador.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
@ConfigurationProperties("rinos.account-creation")
public record AccountCreationPropertiesConfig(
    @DefaultValue("0") int turnstileRequiredAfter,
    @DefaultValue("15m") Duration originWindow,
    @DefaultValue("5") int originLimit,
    @DefaultValue("15m") Duration originBlockPeriod,
    @DefaultValue("30d") Duration idempotencyRetention,
    @DefaultValue("25") int outboxBatchSize,
    @DefaultValue("2m") Duration outboxLease,
    @DefaultValue("1m") Duration outboxRetryBase,
    @DefaultValue("1h") Duration outboxRetryMaximum) {

  /**
   * Rejeita parâmetros que tornariam a proteção contra automação, a repetição ou a retomada da
   * outbox indeterminadas.
   *
   * @throws IllegalArgumentException quando algum limite ou período é inválido
   */
  public AccountCreationPropertiesConfig {
    if (turnstileRequiredAfter < 0 || originLimit <= 0 || outboxBatchSize <= 0
        || invalid(originWindow) || invalid(originBlockPeriod) || invalid(idempotencyRetention)
        || invalid(outboxLease) || invalid(outboxRetryBase) || invalid(outboxRetryMaximum)
        || outboxRetryMaximum.compareTo(outboxRetryBase) < 0) {
      throw new IllegalArgumentException("account creation properties are invalid");
    }
  }

  private static boolean invalid(Duration value) {
    return value == null || value.isZero() || value.isNegative();
  }
}
