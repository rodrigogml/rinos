package br.com.rinos.app.api.module.storage.enums;

/**
 * Resultados seguros da solicitação idempotente de desativação lógica do armazenamento.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public enum TenantStorageDeactivationStatusEnum {
  DEACTIVATION_REQUESTED,
  ALREADY_DEACTIVATING,
  ALREADY_INACTIVE,
  TENANT_NOT_FOUND,
  STORAGE_NOT_REGISTERED
}
