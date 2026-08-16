package br.com.rinos.app.api.module.plans.enums;

/** Resultado fechado de bootstrap idempotente de contrato. */
public enum ContractBootstrapStatus {
  COMPLETED,
  ALREADY_COMPLETED,
  REJECTED,
  UNAVAILABLE
}
