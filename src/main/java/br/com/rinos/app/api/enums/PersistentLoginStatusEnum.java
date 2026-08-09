package br.com.rinos.app.api.enums;

/**
 * Resultados seguros do ciclo HTTP de uma credencial persistente.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum PersistentLoginStatusEnum {
  ABSENT,
  RESTORED,
  INVALID,
  EXPIRED,
  REVOKED,
  BLOCKED,
  REPLAY_DETECTED
}
