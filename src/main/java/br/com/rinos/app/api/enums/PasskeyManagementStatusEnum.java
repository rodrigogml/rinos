package br.com.rinos.app.api.enums;

/**
 * Resultados publicos da gestao individual de passkeys.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public enum PasskeyManagementStatusEnum {
  COMPLETED,
  REJECTED,
  STALE,
  LAST_METHOD,
  ADMIN_FACTOR_REQUIRED,
  ACCESS_DENIED,
  UNAVAILABLE
}
