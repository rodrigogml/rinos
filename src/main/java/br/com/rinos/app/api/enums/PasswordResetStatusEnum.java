package br.com.rinos.app.api.enums;

/**
 * Resultados públicos da redefinição de senha.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public enum PasswordResetStatusEnum {
  COMPLETED,
  INVALID_PROOF,
  EXPIRED_PROOF,
  RATE_LIMITED,
  VALIDATION_REJECTED,
  UNAVAILABLE
}
