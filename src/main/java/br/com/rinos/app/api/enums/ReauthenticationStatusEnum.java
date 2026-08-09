package br.com.rinos.app.api.enums;

/**
 * Resultados públicos do protocolo de reautenticação autenticado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum ReauthenticationStatusEnum {

  ALREADY_RECENT,
  CHALLENGE_REQUIRED,
  COMPLETED,
  REJECTED,
  EXPIRED,
  CONFLICT,
  ACCESS_DENIED,
  UNAVAILABLE
}
