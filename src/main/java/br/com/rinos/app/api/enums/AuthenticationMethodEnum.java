package br.com.rinos.app.api.enums;

/**
 * Métodos de autenticação publicados pela fronteira interna.
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
