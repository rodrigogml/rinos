package br.com.rinos.app.backend.module.storage.vo;

import java.util.Objects;

import br.com.rinos.app.backend.module.storage.enums.TenantStorageDivergenceType;

/**
 * Diagnóstico seguro de uma divergência conhecida pelo catálogo global, sem localização física ou conexão.
 *
 * @param tenantStorageRegistryId identificador interno do registro afetado
 * @param type classificação estável da divergência observada
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageDivergenceVO(Long tenantStorageRegistryId, TenantStorageDivergenceType type) {

  /** Rejeita diagnóstico sem registro conhecido ou classificação explícita. */
  public TenantStorageDivergenceVO {
    Objects.requireNonNull(tenantStorageRegistryId, "tenantStorageRegistryId must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }
}
