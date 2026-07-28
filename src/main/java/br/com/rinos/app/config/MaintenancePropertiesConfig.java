package br.com.rinos.app.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define a identidade da instância e os tempos da eleição automática de manutenção.
 *
 * @param instanceId identidade explícita e exclusiva da instância
 * @param heartbeatInterval intervalo entre heartbeats da sessão líder
 * @param leaseTimeout tempo sem heartbeat que permite nova eleição
 * @param stabilizationPeriod espera antes de a nova líder executar tarefas
 * @param batchTransactionTimeout timeout máximo de cada transação de lote
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.maintenance")
public record MaintenancePropertiesConfig(
    String instanceId,
    @DefaultValue("30m") Duration heartbeatInterval,
    @DefaultValue("4h") Duration leaseTimeout,
    @DefaultValue("10m") Duration stabilizationPeriod,
    @DefaultValue("5m") Duration batchTransactionTimeout) {

  /**
   * Valida a ordem temporal necessária para eleição e fencing seguros.
   */
  public MaintenancePropertiesConfig {
    if (instanceId == null || instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId é obrigatório.");
    }
    if (!isPositive(heartbeatInterval) || !isPositive(leaseTimeout)
        || !isPositive(stabilizationPeriod) || !isPositive(batchTransactionTimeout)) {
      throw new IllegalArgumentException("Os tempos de manutenção devem ser maiores que zero.");
    }
    if (heartbeatInterval.compareTo(leaseTimeout) >= 0) {
      throw new IllegalArgumentException("heartbeatInterval deve ser menor que leaseTimeout.");
    }
    if (batchTransactionTimeout.compareTo(stabilizationPeriod) >= 0) {
      throw new IllegalArgumentException(
          "batchTransactionTimeout deve ser menor que stabilizationPeriod.");
    }
  }

  private static boolean isPositive(Duration value) {
    return value != null && !value.isNegative() && !value.isZero();
  }
}
