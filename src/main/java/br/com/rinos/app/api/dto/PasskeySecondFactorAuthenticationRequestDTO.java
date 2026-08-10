package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Vincula uma assertion WebAuthn validada a um desafio de segundo fator já aberto.
 *
 * @param challengeReference referência opaca do fluxo
 * @param userHandle identificador público aleatório do proprietário WebAuthn
 * @param validatedAt instante da validação criptográfica pelo endpoint
 * @param correlationId correlação técnica sem dados da credencial
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeySecondFactorAuthenticationRequestDTO(
    String challengeReference,
    byte[] userHandle,
    Instant validatedAt,
    UUID correlationId) {

  public PasskeySecondFactorAuthenticationRequestDTO {
    if (challengeReference == null || challengeReference.isBlank()) {
      throw new IllegalArgumentException("challengeReference must not be blank");
    }
    Objects.requireNonNull(userHandle, "userHandle must not be null");
    if (userHandle.length < 16 || userHandle.length > 64) {
      throw new IllegalArgumentException("userHandle length must be between 16 and 64 bytes");
    }
    userHandle = Arrays.copyOf(userHandle, userHandle.length);
    Objects.requireNonNull(validatedAt, "validatedAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  @Override
  public byte[] userHandle() {
    return Arrays.copyOf(userHandle, userHandle.length);
  }

  /** Não revela continuação ou identificador WebAuthn em diagnósticos. */
  @Override
  public String toString() {
    return "PasskeySecondFactorAuthenticationRequestDTO[challengeReference=REDACTED, "
        + "userHandle=REDACTED, validatedAt=" + validatedAt
        + ", correlationId=" + correlationId + "]";
  }
}
