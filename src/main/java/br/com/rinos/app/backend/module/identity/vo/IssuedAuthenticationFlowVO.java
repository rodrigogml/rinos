package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta a referência bruta somente entre a emissão e a camada consumidora imediata.
 *
 * @param reference referência opaca que não será persistida
 * @param expiresAt limite UTC da continuação
 * @param correlationId correlação sanitizada da operação
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record IssuedAuthenticationFlowVO(
    String reference,
    Instant expiresAt,
    UUID correlationId) {

  public IssuedAuthenticationFlowVO {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  @Override
  public String toString() {
    return "IssuedAuthenticationFlowVO[reference=REDACTED, expiresAt="
        + expiresAt + ", correlationId=" + correlationId + "]";
  }
}
