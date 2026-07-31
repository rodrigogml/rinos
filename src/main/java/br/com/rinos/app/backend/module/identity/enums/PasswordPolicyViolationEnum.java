package br.com.rinos.app.backend.module.identity.enums;

/**
 * Motivos públicos e localizáveis de rejeição de uma senha.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum PasswordPolicyViolationEnum {

  MINIMUM_LENGTH_REQUIRED,
  MAXIMUM_LENGTH_EXCEEDED,
  UPPERCASE_REQUIRED,
  LOWERCASE_REQUIRED,
  NUMBER_REQUIRED,
  SPECIAL_CHARACTER_REQUIRED,
  COMMON_PASSWORD,
  COMPROMISED_PASSWORD,
  COMPROMISE_CHECK_UNAVAILABLE
}
