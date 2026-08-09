package br.com.rinos.app.api.dto;

import java.time.Instant;

/**
 * Solicitação pública transitória de consumo do código informado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record EmailOtpVerificationRequestDTO(
    String challengeReference,
    String proof,
    Instant occurredAt) {

  @Override
  public String toString() {
    return "EmailOtpVerificationRequestDTO[challengeReference=" + challengeReference
        + ", occurredAt=" + occurredAt + ", proof=REDACTED]";
  }
}
