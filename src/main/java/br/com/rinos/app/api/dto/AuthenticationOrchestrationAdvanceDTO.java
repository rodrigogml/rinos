package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/**
 * Registra no fluxo uma comprovação adicional já validada e consumida pelo serviço do fator.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationOrchestrationAdvanceDTO(
    String reference,
    AuthenticationMethodEnum method,
    Instant verifiedAt,
    Boolean userVerification,
    Instant occurredAt) {

  public AuthenticationOrchestrationAdvanceDTO {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }

  /** Oculta a referência opaca em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationOrchestrationAdvanceDTO[reference=REDACTED, method=" + method
        + ", verifiedAt=" + verifiedAt + ", userVerification=" + userVerification
        + ", occurredAt=" + occurredAt + "]";
  }
}
