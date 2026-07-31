package br.com.rinos.app.api.enums;

/**
 * Resultados públicos da conclusão de um cadastro por identidade externa.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum ExternalRegistrationCompletionStatusEnum {

  AUTHENTICATED,
  INVALID_REFERENCE,
  EXPIRED_REFERENCE,
  VALIDATION_REJECTED,
  CONFLICT,
  UNAVAILABLE
}
