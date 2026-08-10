package br.com.rinos.app.backend.module.identity.enums;

/**
 * Motivos sanitizados de comportamento anormal observado na borda WebAuthn.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public enum PasskeyRiskReasonEnum {
  CREDENTIAL_NOT_USABLE,
  OWNER_MISMATCH,
  IMMUTABLE_MATERIAL_MISMATCH,
  SIGNATURE_COUNTER_REGRESSION,
  BACKUP_STATE_INCONSISTENT
}
