package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta somente a identidade WebAuthn já validada até a fachada de autenticação.
 *
 * @param userHandle identificador público aleatório e estável do usuário WebAuthn
 * @param validatedAt instante da validação criptográfica pelo endpoint
 * @param correlationId correlação técnica sem dados da credencial
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeyAuthenticationRequestDTO(
    byte[] userHandle,
    Instant validatedAt,
    UUID correlationId) {

  public PasskeyAuthenticationRequestDTO {
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

  @Override
  public String toString() {
    return "PasskeyAuthenticationRequestDTO[userHandle=REDACTED, validatedAt="
        + validatedAt + ", correlationId=" + correlationId + "]";
  }
}
