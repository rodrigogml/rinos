package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta somente a chave estável Google já validada até a fachada de autenticação.
 *
 * @param issuer emissor validado pelo provider
 * @param subject identificador estável validado pelo provider
 * @param validatedAt instante em que a prova externa terminou de ser validada
 * @param correlationId correlação técnica sem dados da identidade
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record GoogleAuthenticationRequestDTO(
    String issuer,
    String subject,
    Instant validatedAt,
    UUID correlationId) {

  public GoogleAuthenticationRequestDTO {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("issuer must not be blank");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject must not be blank");
    }
    Objects.requireNonNull(validatedAt, "validatedAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  /** Não revela a chave estável da identidade em diagnósticos. */
  @Override
  public String toString() {
    return "GoogleAuthenticationRequestDTO[issuer=REDACTED, subject=REDACTED, validatedAt="
        + validatedAt + ", correlationId=" + correlationId + "]";
  }
}
