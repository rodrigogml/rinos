package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrega efêmera de uma sessão recém-criada.
 *
 * <p>O cookie bruto existe apenas neste retorno e é ocultado de {@link #toString()}.
 *
 * @param cookieValue selector e validator destinados exclusivamente ao cookie seguro
 * @param publicReference referência de gestão que não autentica
 * @param absoluteExpiresAt limite absoluto UTC
 * @param idleExpiresAt limite inicial de inatividade UTC
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record IssuedAuthSessionVO(
    String cookieValue,
    UUID publicReference,
    Instant absoluteExpiresAt,
    Instant idleExpiresAt) {

  public IssuedAuthSessionVO {
    if (cookieValue == null || cookieValue.isBlank()) {
      throw new IllegalArgumentException("cookieValue must not be blank");
    }
    Objects.requireNonNull(publicReference, "publicReference must not be null");
    Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
    Objects.requireNonNull(idleExpiresAt, "idleExpiresAt must not be null");
  }

  /** Impede exposição acidental do cookie em logs. */
  @Override
  public String toString() {
    return "IssuedAuthSessionVO[cookieValue=<redacted>, publicReference="
        + publicReference + ", absoluteExpiresAt=" + absoluteExpiresAt
        + ", idleExpiresAt=" + idleExpiresAt + "]";
  }
}
