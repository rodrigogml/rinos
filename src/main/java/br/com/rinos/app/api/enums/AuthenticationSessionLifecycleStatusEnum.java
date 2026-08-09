package br.com.rinos.app.api.enums;

/**
 * Estados públicos seguros do lifecycle da sessão autenticada.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum AuthenticationSessionLifecycleStatusEnum {
  PREPARED,
  ACTIVE,
  INVALID,
  EXPIRED,
  REVOKED,
  BLOCKED,
  UNAVAILABLE
}
