package br.com.rinos.app.api.module.storage.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.module.storage.enums.TenantStorageCreatorStatusEnum;

/**
 * Estado público mínimo da preparação de armazenamento destinado ao criador da conta.
 *
 * @param status estado público sem detalhes de infraestrutura
 * @param observedAt instante UTC da última observação segura
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageCreatorSummaryVO(TenantStorageCreatorStatusEnum status,
    Instant observedAt) {

  /**
   * Exige os campos mínimos necessários para apresentar o resumo público.
   *
   * @throws NullPointerException quando status ou instante não forem informados
   */
  public TenantStorageCreatorSummaryVO {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(observedAt, "observedAt must not be null");
  }
}
