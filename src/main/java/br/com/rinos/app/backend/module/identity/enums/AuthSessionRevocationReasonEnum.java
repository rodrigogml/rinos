package br.com.rinos.app.backend.module.identity.enums;

/**
 * Motivos fechados e auditáveis para revogação de sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthSessionRevocationReasonEnum {
  USER_REQUEST,
  OTHER_SESSIONS,
  USER_NOT_ACTIVE,
  VALIDATOR_MISMATCH,
  SECURITY_EVENT
}
