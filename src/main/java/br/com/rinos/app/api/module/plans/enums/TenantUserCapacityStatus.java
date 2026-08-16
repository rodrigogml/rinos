package br.com.rinos.app.api.module.plans.enums;

/** Resultado de reserva ou ocupação da capacidade de usuários do tenant. */
public enum TenantUserCapacityStatus {
  AVAILABLE,
  RESERVED,
  OCCUPIED,
  ALREADY_RESERVED,
  ALREADY_OCCUPIED,
  RELEASED,
  LIMIT_REACHED,
  INVALID_CONTEXT,
  REJECTED,
  SOURCE_UNAVAILABLE
}
