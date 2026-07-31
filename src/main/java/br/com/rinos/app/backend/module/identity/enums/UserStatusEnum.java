package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados persistentes da identidade global de um usuário.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum UserStatusEnum {

  PENDING_VERIFICATION,
  ACTIVE,
  BLOCKED,
  DEACTIVATED,
  CANCELLED
}
