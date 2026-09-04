package br.com.rinos.app.api.enums;

/**
 * Operações públicas que podem exigir comprovação humana antes de alcançar um caso de uso.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum HumanVerificationOperationEnum {

  SIGN_IN,
  REGISTRATION,
  REGISTRATION_CANCELLATION,
  PASSWORD_RECOVERY,
  ACCOUNT_CREATION
}
