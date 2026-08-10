package br.com.rinos.app.api.dto;

import java.time.Instant;

import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/**
 * Transporta uma prova adicional vinculada à continuação opaca corrente.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record SecondFactorVerificationRequestDTO(
    String challengeReference,
    AuthenticationMethodEnum method,
    String proof,
    Instant occurredAt) {

  /** Não permite que a prova apareça em logs acidentais do DTO. */
  @Override
  public String toString() {
    return "SecondFactorVerificationRequestDTO[challengeReference=REDACTED, method=" + method
        + ", occurredAt=" + occurredAt + ", proof=REDACTED]";
  }
}
