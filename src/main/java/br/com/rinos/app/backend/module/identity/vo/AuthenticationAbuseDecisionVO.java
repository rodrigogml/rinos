package br.com.rinos.app.backend.module.identity.vo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Combina as políticas independentes do identificador e da origem sem expor suas chaves.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record AuthenticationAbuseDecisionVO(
    int maximumFailureCount,
    boolean turnstileRequired,
    Duration retryAfter,
    Instant turnstileRequiredUntil) {

  public AuthenticationAbuseDecisionVO {
    if (maximumFailureCount < 0) {
      throw new IllegalArgumentException("maximumFailureCount must not be negative");
    }
    Objects.requireNonNull(retryAfter, "retryAfter must not be null");
  }

  /** Decisão vazia usada quando não existe histórico em nenhuma dimensão. */
  public static AuthenticationAbuseDecisionVO clear() {
    return new AuthenticationAbuseDecisionVO(0, false, Duration.ZERO, null);
  }
}
