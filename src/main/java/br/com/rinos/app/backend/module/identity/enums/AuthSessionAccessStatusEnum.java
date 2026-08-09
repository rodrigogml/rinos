package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados seguros da validação de uma sessão global.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthSessionAccessStatusEnum {
  ACTIVE,
  ROTATED,
  REJECTED,
  BLOCKED,
  REPLAY_DETECTED,
  REVOKED,
  EXPIRED
}
