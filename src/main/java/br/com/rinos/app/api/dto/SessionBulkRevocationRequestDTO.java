package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Solicita revogação abrangente de sessões próprias.
 *
 * @param context contexto autenticado corrente
 * @param keepCurrent preserva a sessão solicitante quando {@code true}
 * @param correlationId correlação auditável da operação
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record SessionBulkRevocationRequestDTO(
    SessionManagementContextDTO context,
    boolean keepCurrent,
    UUID correlationId) {

  public SessionBulkRevocationRequestDTO {
    if (context == null) {
      throw new IllegalArgumentException("context must not be null");
    }
    if (correlationId == null) {
      throw new IllegalArgumentException("correlationId must not be null");
    }
  }

  /** Redige a identidade da sessão corrente em diagnósticos. */
  @Override
  public String toString() {
    return "SessionBulkRevocationRequestDTO[context=REDACTED, keepCurrent="
        + keepCurrent + ", correlationId=" + correlationId + "]";
  }
}
