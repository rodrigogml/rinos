package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define os limites fixos usados pela fila estrutural de provisionamento e atualização de tenants.
 *
 * <p>Esses valores pertencem à instalação e são lidos exclusivamente do {@code application.properties}; não são
 * configuráveis por tenant nem por administrador do sistema.</p>
 *
 * @param queuePollInterval intervalo de consulta da fila estrutural pela instância de manutenção eleita
 * @param operationLease tempo máximo de posse de uma operação estrutural sem renovação
 * @param operationHeartbeatInterval intervalo de renovação do lease da operação em execução
 * @param provisioningMaximumAttempts máximo de tentativas automáticas para falha transitória de provisionamento
 * @param maximumConcurrentOperations máximo de operações estruturais executadas simultaneamente na instalação
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
@ConfigurationProperties("rinos.storage")
public record StoragePropertiesConfig(
    @DefaultValue("30s") Duration queuePollInterval,
    @DefaultValue("10m") Duration operationLease,
    @DefaultValue("30s") Duration operationHeartbeatInterval,
    @DefaultValue("3") int provisioningMaximumAttempts,
    @DefaultValue("1") int maximumConcurrentOperations) {

  /**
   * Rejeita valores que fariam a execução, a retomada ou a concorrência estrutural perderem significado.
   *
   * @throws IllegalArgumentException quando um limite não é positivo ou o heartbeat não antecede o lease
   */
  public StoragePropertiesConfig {
    if (invalid(queuePollInterval) || invalid(operationLease) || invalid(operationHeartbeatInterval)
        || provisioningMaximumAttempts <= 0 || maximumConcurrentOperations <= 0
        || operationHeartbeatInterval.compareTo(operationLease) >= 0) {
      throw new IllegalArgumentException("storage properties are invalid");
    }
  }

  private static boolean invalid(Duration value) {
    return value == null || value.isZero() || value.isNegative();
  }
}
