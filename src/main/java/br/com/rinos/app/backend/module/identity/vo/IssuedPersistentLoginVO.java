package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrega interna e efêmera do autenticador persistente de uma sessão já publicada.
 *
 * @param cookieValue seletor e validador destinados exclusivamente ao cookie HTTP
 * @param publicReference referência não autenticadora da sessão
 * @param absoluteExpiresAt limite absoluto que também limita o cookie persistente
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record IssuedPersistentLoginVO(
    String cookieValue,
    UUID publicReference,
    Instant absoluteExpiresAt) {

  public IssuedPersistentLoginVO {
    if (cookieValue == null || cookieValue.isBlank()) {
      throw new IllegalArgumentException("cookieValue must not be blank");
    }
    Objects.requireNonNull(publicReference, "publicReference must not be null");
    Objects.requireNonNull(absoluteExpiresAt, "absoluteExpiresAt must not be null");
  }

  /** Evita expor a credencial efêmera em diagnósticos. */
  @Override
  public String toString() {
    return "IssuedPersistentLoginVO[cookieValue=<redacted>, publicReference="
        + publicReference + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
  }
}
