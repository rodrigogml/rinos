package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados fechados do ciclo de reautenticação de uma operação sensível.
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
  ACCESS_DENIED
}
