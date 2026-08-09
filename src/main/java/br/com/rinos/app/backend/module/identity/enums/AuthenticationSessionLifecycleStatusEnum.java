package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos fechados do lifecycle da sessão autenticada.
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
  BLOCKED
}
