package br.com.rinos.app.api.enums;

/** Resultados públicos do ciclo de enrollment TOTP. */
public enum TotpEnrollmentStatusEnum {
  PENDING,
  ACTIVE,
  REJECTED,
  EXPIRED,
  ATTEMPTS_EXHAUSTED,
  STALE,
  ACCESS_DENIED,
  UNAVAILABLE
}
