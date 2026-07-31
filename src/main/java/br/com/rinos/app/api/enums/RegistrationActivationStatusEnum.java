package br.com.rinos.app.api.enums;

/**
 * Resultados públicos da ativação de um cadastro local.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationActivationStatusEnum {

  ACTIVATED,
  ALREADY_ACTIVE,
  CONSENT_REQUIRED,
  INVALID_PROOF,
  EXPIRED_PROOF,
  REGISTRATION_CLOSED,
  VALIDATION_REJECTED,
  UNAVAILABLE
}
