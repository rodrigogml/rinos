package br.com.rinos.app.backend.module.account.service;

import java.time.Duration;

/**
 * Resultado interno e seguro da admissão antes de uma nova intenção de conta.
 *
 * @param admitted indica que a cota foi reservada
 * @param safeReasonCode motivo estável quando negada
 * @param retryAfter espera mínima quando limitada, ou {@code null}
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
public record AccountCreationAdmissionResult(
    boolean admitted,
    String safeReasonCode,
    Duration retryAfter) {

  /** Cria a decisão de admissão. */
  public static AccountCreationAdmissionResult permitted() {
    return new AccountCreationAdmissionResult(true, null, null);
  }

  /** Cria a decisão que pede prova humana válida. */
  public static AccountCreationAdmissionResult humanVerificationRequired() {
    return new AccountCreationAdmissionResult(
        false, "ACCOUNT_HUMAN_VERIFICATION_REQUIRED", null);
  }

  /** Cria a decisão limitada pela origem. */
  public static AccountCreationAdmissionResult rateLimited(Duration retryAfter) {
    return new AccountCreationAdmissionResult(
        false, "ACCOUNT_RATE_LIMITED", retryAfter);
  }
}
