package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos da gestão autenticada de identidades externas.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public enum ExternalIdentityOperationStatusEnum {

  LINKED,
  ALREADY_LINKED,
  UNLINKED,
  REJECTED,
  CONFLICT,
  LAST_METHOD,
  STALE,
  ACCESS_DENIED
}
