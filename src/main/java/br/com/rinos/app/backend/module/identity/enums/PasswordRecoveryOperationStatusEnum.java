package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos reduzidos das operações de recuperação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public enum PasswordRecoveryOperationStatusEnum {
  ACCEPTED,
  COMPLETED,
  INVALID_PROOF,
  EXPIRED_PROOF,
  RATE_LIMITED
}
