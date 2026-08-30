package br.com.rinos.app.api.module.storage.enums;

/**
 * Classes seguras de divergência estrutural identificáveis pela reconciliação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public enum TenantStorageDivergenceTypeEnum {
  REGISTRY_SCHEMA_MISSING,
  OPERATION_WITHOUT_PROGRESS
}
