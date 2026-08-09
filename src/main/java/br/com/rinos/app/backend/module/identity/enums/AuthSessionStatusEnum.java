package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados persistentes de uma sessão global.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthSessionStatusEnum {
  PREPARED,
  ACTIVE,
  REVOKED,
  EXPIRED
}
