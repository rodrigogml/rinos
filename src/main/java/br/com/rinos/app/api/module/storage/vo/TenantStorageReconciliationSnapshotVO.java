package br.com.rinos.app.api.module.storage.vo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Fotografia exclusivamente diagnóstica da reconciliação física de tenants.
 *
 * @param divergences registros globais com divergência detectada
 * @param unregisteredSchemaCount quantidade de schemas internos sem registro global correspondente
 * @param observedAt instante UTC da leitura
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageReconciliationSnapshotVO(List<TenantStorageDivergenceVO> divergences,
    int unregisteredSchemaCount, Instant observedAt) {

  /** Protege a fotografia contra coleção mutável ou quantidade negativa. */
  public TenantStorageReconciliationSnapshotVO {
    Objects.requireNonNull(divergences, "divergences must not be null");
    Objects.requireNonNull(observedAt, "observedAt must not be null");
    if (unregisteredSchemaCount < 0) {
      throw new IllegalArgumentException("unregisteredSchemaCount must not be negative");
    }
    divergences = List.copyOf(divergences);
  }
}
