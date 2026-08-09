package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Solicita a revogação idempotente de uma sessão própria.
 *
 * @param context contexto autenticado corrente
 * @param targetSessionReference referência opaca selecionada da listagem vigente
 * @param correlationId correlação auditável da operação
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record SessionRevocationRequestDTO(
    SessionManagementContextDTO context,
    String targetSessionReference,
    UUID correlationId) {

  public SessionRevocationRequestDTO {
    if (context == null) {
      throw new IllegalArgumentException("context must not be null");
    }
    if (targetSessionReference == null || targetSessionReference.isBlank()) {
      throw new IllegalArgumentException("targetSessionReference must not be blank");
    }
    if (correlationId == null) {
      throw new IllegalArgumentException("correlationId must not be null");
    }
  }

  /** Redige referências em diagnósticos. */
  @Override
  public String toString() {
    return "SessionRevocationRequestDTO[context=REDACTED, "
        + "targetSessionReference=REDACTED, correlationId=" + correlationId + "]";
  }
}
