package br.com.rinos.app.backend.module.identity.enums;

/** Resultados internos seguros do ciclo de enrollment TOTP. */
public enum TotpEnrollmentStatusEnum {
  PENDING,
  ACTIVE,
  REJECTED,
  EXPIRED,
  ATTEMPTS_EXHAUSTED,
  STALE,
  ACCESS_DENIED
}
