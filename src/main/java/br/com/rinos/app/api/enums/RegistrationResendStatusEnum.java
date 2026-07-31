package br.com.rinos.app.api.enums;

/**
 * Resultados públicos de uma solicitação de nova comprovação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationResendStatusEnum {
  REQUEST_ACCEPTED,
  EMAIL_DISPATCH_FAILED,
  RATE_LIMITED,
  VALIDATION_REJECTED,
  UNAVAILABLE
}
