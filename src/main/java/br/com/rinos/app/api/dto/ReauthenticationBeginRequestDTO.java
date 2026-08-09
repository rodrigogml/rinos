package br.com.rinos.app.api.dto;

import java.time.Instant;

/**
 * Solicita a garantia recente para uma operação interna do catálogo.
 *
 * @param userId identidade da sessão autenticada
 * @param sessionReference referência não autenticadora da sessão corrente
 * @param operationId identificador interno e estável da operação
 * @param occurredAt instante UTC da decisão
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record ReauthenticationBeginRequestDTO(
    long userId,
    String sessionReference,
    String operationId,
    Instant occurredAt) {

  /** Valida os dados derivados exclusivamente do contexto autenticado e do renderer RFW. */
  public ReauthenticationBeginRequestDTO {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (sessionReference == null || sessionReference.isBlank()) {
      throw new IllegalArgumentException("sessionReference must not be blank");
    }
    if (operationId == null || operationId.isBlank()) {
      throw new IllegalArgumentException("operationId must not be blank");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt must not be null");
    }
  }

  /** Redige a sessão e a identidade em diagnósticos. */
  @Override
  public String toString() {
    return "ReauthenticationBeginRequestDTO[userId=REDACTED, sessionReference=REDACTED, "
        + "operationId=" + operationId + ", occurredAt=" + occurredAt + "]";
  }
}
