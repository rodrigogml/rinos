package br.com.rinos.app.backend.module.platform.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifica uma execução específica da instância durante a coordenação de manutenção.
 *
 * @param instanceId identidade estável configurada para a instância
 * @param sessionId identidade efêmera gerada para esta inicialização
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record MaintenanceSessionVO(String instanceId, UUID sessionId) {

  /**
   * Valida as duas identidades necessárias ao fencing entre reinicializações.
   *
   * @throws IllegalArgumentException quando a identidade da instância está vazia
   * @throws NullPointerException quando alguma identidade é nula
   */
  public MaintenanceSessionVO {
    Objects.requireNonNull(instanceId, "instanceId must not be null");
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    if (instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId must not be blank");
    }
  }
}
