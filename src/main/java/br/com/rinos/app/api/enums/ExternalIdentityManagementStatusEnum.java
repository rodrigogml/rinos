package br.com.rinos.app.api.enums;

/**
 * Resultados públicos seguros da gestão de identidades externas.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public enum ExternalIdentityManagementStatusEnum {

  COMPLETED,
  REJECTED,
  CONFLICT,
  LAST_METHOD,
  STALE,
  ACCESS_DENIED,
  UNAVAILABLE
}
