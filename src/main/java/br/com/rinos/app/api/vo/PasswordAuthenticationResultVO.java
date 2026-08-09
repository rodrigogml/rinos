package br.com.rinos.app.api.vo;

import java.time.Duration;
import java.util.Objects;

/**
 * Resultado do primeiro fator por senha com a política pública contra abuso.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record PasswordAuthenticationResultVO(
    AuthenticationOrchestrationResultVO orchestration,
    boolean turnstileRequired,
    Duration retryAfter) {

  public PasswordAuthenticationResultVO {
    Objects.requireNonNull(orchestration, "orchestration must not be null");
    retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
    if (retryAfter.isNegative()) {
      throw new IllegalArgumentException("retryAfter must not be negative");
    }
  }
}
