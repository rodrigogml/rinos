package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Objects;

/**
 * Contexto mínimo de uma operação autenticada sobre sessões próprias.
 *
 * @param userId identidade global solicitante
 * @param currentSessionReference referência não autenticadora da sessão corrente
 * @param occurredAt instante UTC da operação
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record SessionManagementContextDTO(
    long userId,
    String currentSessionReference,
    Instant occurredAt) {

  public SessionManagementContextDTO {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (currentSessionReference == null || currentSessionReference.isBlank()) {
      throw new IllegalArgumentException("currentSessionReference must not be blank");
    }
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  /** Redige a identidade e a referência em diagnósticos. */
  @Override
  public String toString() {
    return "SessionManagementContextDTO[userId=REDACTED, "
        + "currentSessionReference=REDACTED, occurredAt=" + occurredAt + "]";
  }
}
