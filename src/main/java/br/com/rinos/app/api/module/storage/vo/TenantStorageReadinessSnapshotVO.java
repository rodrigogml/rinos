package br.com.rinos.app.api.module.storage.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;

/**
 * Fotografia segura da prontidão estrutural de um tenant para consumo por outros módulos.
 *
 * @param sourceAvailable informa se a fonte global pôde ser consultada
 * @param tenantKnown informa se o tenant solicitado existe no contexto global
 * @param ready informa se o armazenamento está pronto e na versão exata esperada
 * @param availability estado seguro que não inclui localização ou configuração física
 * @param safeReasonCode código seguro opcional para orientar o consumidor
 * @param observedAt instante UTC em que a fotografia foi observada
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record TenantStorageReadinessSnapshotVO(boolean sourceAvailable, boolean tenantKnown,
    boolean ready, TenantStorageAvailabilityEnum availability, String safeReasonCode,
    Instant observedAt) {

  /**
   * Valida a coerência interna da fotografia antes de ela atravessar a fronteira de módulos.
   *
   * @throws NullPointerException quando disponibilidade ou instante não forem informados
   * @throws IllegalArgumentException quando prontidão e disponibilidade forem incompatíveis
   */
  public TenantStorageReadinessSnapshotVO {
    Objects.requireNonNull(availability, "availability must not be null");
    Objects.requireNonNull(observedAt, "observedAt must not be null");
    if (ready && (!sourceAvailable || !tenantKnown || availability != TenantStorageAvailabilityEnum.READY)) {
      throw new IllegalArgumentException("ready snapshot requires an available known tenant in READY state");
    }
    if (!ready && availability == TenantStorageAvailabilityEnum.READY) {
      throw new IllegalArgumentException("READY availability requires a ready snapshot");
    }
    if (safeReasonCode != null && safeReasonCode.isBlank()) {
      throw new IllegalArgumentException("safeReasonCode must not be blank");
    }
  }
}
