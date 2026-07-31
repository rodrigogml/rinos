package br.com.rinos.app.api.enums;

/**
 * Resultados públicos do início do cadastro local.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationStartStatusEnum {
  EMAIL_SENT,
  EMAIL_DISPATCH_FAILED,
  EMAIL_ALREADY_EXISTS,
  PENDING_ALREADY_EXISTS,
  RATE_LIMITED,
  VALIDATION_REJECTED,
  UNAVAILABLE
}
