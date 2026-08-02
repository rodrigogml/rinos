package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados persistentes de uma prova de recuperação de senha.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public enum PasswordRecoveryStatusEnum {
  OPEN,
  USED,
  INVALIDATED,
  EXPIRED
}
