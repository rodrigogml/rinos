package br.com.rinos.app.api.module.storage.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.storage.enums.TenantStorageDivergenceTypeEnum;

/** Divergência sanitizada vinculada somente ao registro global interno. */
public record TenantStorageDivergenceVO(
    Long tenantStorageRegistryId,
    TenantStorageDivergenceTypeEnum type) {

  /** Impede divulgar uma divergência sem a referência global e a classificação fechada. */
  public TenantStorageDivergenceVO {
    if (tenantStorageRegistryId == null || tenantStorageRegistryId <= 0) {
      throw new IllegalArgumentException("tenantStorageRegistryId must be positive");
    }
    Objects.requireNonNull(type, "type must not be null");
  }
}
