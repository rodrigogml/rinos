package br.com.rinos.app.backend.module.identity.vo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Política resultante da janela antifraude sem revelar seu identificador protegido.
 *
 * @param failureCount falhas acumuladas na janela
 * @param turnstileRequired indica exigência vigente de verificação humana
 * @param retryDelay espera progressiva antes da próxima tentativa
 * @param windowEndsAt fim da janela corrente
 * @param turnstileRequiredUntil fim da exigência ou {@code null}
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationWindowDecisionVO(
    int failureCount,
    boolean turnstileRequired,
    Duration retryDelay,
    Instant windowEndsAt,
    Instant turnstileRequiredUntil) {

  public AuthenticationWindowDecisionVO {
    if (failureCount < 0) {
      throw new IllegalArgumentException("failureCount must not be negative");
    }
    Objects.requireNonNull(retryDelay, "retryDelay must not be null");
    Objects.requireNonNull(windowEndsAt, "windowEndsAt must not be null");
  }
}
