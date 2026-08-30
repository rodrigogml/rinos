package br.com.rinos.app.api.module.storage.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento interno seguro que anuncia a aceitação de uma operação estrutural na fila global.
 *
 * @param operationPublicId identificador público estável da operação aceita
 * @param tenantPublicId identificador público do tenant lógico afetado
 * @param correlationId correlação técnica sem segredo ou localização física
 * @param occurredAt instante UTC de aceitação da operação
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageOperationQueuedEventVO(UUID operationPublicId, UUID tenantPublicId,
    String correlationId, Instant occurredAt) {

  /**
   * Valida os dados mínimos seguros usados entre a reserva e o despachante durável.
   *
   * @throws NullPointerException quando identificadores ou instante não forem informados
   * @throws IllegalArgumentException quando a correlação estiver ausente ou exceder o limite persistido
   */
  public TenantStorageOperationQueuedEventVO {
    Objects.requireNonNull(operationPublicId, "operationPublicId must not be null");
    Objects.requireNonNull(tenantPublicId, "tenantPublicId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    correlationId = correlationId.strip();
  }
}
