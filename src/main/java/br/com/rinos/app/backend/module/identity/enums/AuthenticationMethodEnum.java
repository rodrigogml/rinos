package br.com.rinos.app.backend.module.identity.enums;

/**
 * Métodos fechados que podem participar de um fluxo de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationMethodEnum {

  PASSWORD,
  GOOGLE,
  PASSKEY,
  TOTP,
  EMAIL_CODE,
  RECOVERY_CODE
}
