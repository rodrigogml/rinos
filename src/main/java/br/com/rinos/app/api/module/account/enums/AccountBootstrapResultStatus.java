package br.com.rinos.app.api.module.account.enums;

/** Resultado fechado de uma etapa idempotente do bootstrap de conta. */
public enum AccountBootstrapResultStatus {
  ACCEPTED,
  ALREADY_COMPLETED,
  UNAVAILABLE,
  REJECTED
}
