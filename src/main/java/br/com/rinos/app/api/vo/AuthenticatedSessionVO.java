package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * Fotografia reconhecível de uma sessão ativa sem material autenticador ou IP bruto.
 *
 * @param reference referência opaca somente para gestão
 * @param current identifica a sessão solicitante
 * @param createdAt instante de publicação
 * @param lastActivityAt última atividade persistida
 * @param deviceDescription descrição sanitizada ou {@code null}
 * @param locationDescription origem aproximada ou {@code null} enquanto indisponível
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record AuthenticatedSessionVO(
    String reference,
    boolean current,
    Instant createdAt,
    Instant lastActivityAt,
    String deviceDescription,
    String locationDescription) {

  public AuthenticatedSessionVO {
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
  }

  /** Evita registrar a referência de gestão. */
  @Override
  public String toString() {
    return "AuthenticatedSessionVO[reference=REDACTED, current=" + current
        + ", createdAt=" + createdAt + ", lastActivityAt=" + lastActivityAt
        + ", deviceDescription=" + deviceDescription
        + ", locationDescription=" + locationDescription + "]";
  }
}
