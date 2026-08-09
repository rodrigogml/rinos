package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;

/**
 * Entrada confiável para preparar a sessão global antes do contexto local.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record AuthenticationSessionPreparationRequestDTO(
    String flowReference,
    AuthenticationFlowPurposeEnum purpose,
    long expectedUserId,
    boolean persistent,
    String canonicalOrigin,
    String userAgent,
    Instant occurredAt) {

  /** Valida somente forma e limites; a autoridade permanece no backend. */
  public AuthenticationSessionPreparationRequestDTO {
    if (flowReference == null || flowReference.isBlank() || flowReference.length() > 512) {
      throw new IllegalArgumentException("flowReference is invalid");
    }
    Objects.requireNonNull(purpose, "purpose must not be null");
    if (expectedUserId <= 0) {
      throw new IllegalArgumentException("expectedUserId must be positive");
    }
    if (canonicalOrigin == null || canonicalOrigin.isBlank() || canonicalOrigin.length() > 64) {
      throw new IllegalArgumentException("canonicalOrigin is invalid");
    }
    if (userAgent != null && userAgent.length() > 2048) {
      throw new IllegalArgumentException("userAgent is too long");
    }
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  /** Redige continuação, identidade, origem e agente em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationSessionPreparationRequestDTO[flowReference=REDACTED, purpose="
        + purpose + ", expectedUserId=REDACTED, persistent=" + persistent
        + ", canonicalOrigin=REDACTED, userAgent=REDACTED, occurredAt=" + occurredAt + "]";
  }
}
