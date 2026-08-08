package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados seguros das alterações de fatores e métodos.
 *
 * @author Rodrigo Leitão
 */
public enum FactorOperationStatusEnum {
  CREATED, ACTIVE, USED, DISABLED, REVOKED, EXHAUSTED, REJECTED, LAST_METHOD, ADMIN_FACTOR_REQUIRED
}
