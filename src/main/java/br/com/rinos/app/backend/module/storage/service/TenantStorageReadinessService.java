package br.com.rinos.app.backend.module.storage.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;
import br.com.rinos.app.api.module.storage.port.TenantStorageReadinessPort;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;

/**
 * Implementa o gate de prontidão pela leitura exclusiva do catálogo global de armazenamento.
 *
 * <p>O serviço deliberadamente não recebe identidade, associação, regra de acesso ou datasource físico. Portanto,
 * sua resposta jamais autoriza uma operação nem amplia acesso quando a fonte global está indisponível.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageReadinessService implements TenantStorageReadinessPort {

  private final TenantRepository tenantRepository;
  private final TenantStorageRegistryRepository registryRepository;
  private final Clock clock;

  /**
   * Cria o gate usando o relógio UTC do processo para registrar a observação segura.
   *
   * @param tenantRepository consulta de tenant no catálogo global
   * @param registryRepository consulta do registro de armazenamento no catálogo global
   */
  public TenantStorageReadinessService(TenantRepository tenantRepository,
      TenantStorageRegistryRepository registryRepository) {
    this(tenantRepository, registryRepository, Clock.systemUTC());
  }

  /**
   * Cria o gate com relógio explícito para testes determinísticos.
   *
   * @param tenantRepository consulta de tenant no catálogo global
   * @param registryRepository consulta do registro de armazenamento no catálogo global
   * @param clock relógio UTC da observação
   */
  TenantStorageReadinessService(TenantRepository tenantRepository,
      TenantStorageRegistryRepository registryRepository, Clock clock) {
    this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
    this.registryRepository = Objects.requireNonNull(registryRepository,
        "registryRepository must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Consulta a prontidão de um tenant e falha fechada quando o catálogo global não puder ser lido.
   *
   * @param tenantPublicId identificador público do tenant, obrigatório
   * @return fotografia segura para gates internos, sem localização física ou dados de conexão
   * @throws NullPointerException quando o identificador não for informado
   */
  @Override
  @Transactional(readOnly = true)
  public TenantStorageReadinessSnapshotVO inspect(UUID tenantPublicId) {
    Objects.requireNonNull(tenantPublicId, "tenantPublicId must not be null");
    Instant observedAt = clock.instant();
    try {
      Optional<TenantEntity> tenant = tenantRepository.findByPublicId(tenantPublicId);
      if (tenant.isEmpty()) {
        return snapshot(true, false, false, TenantStorageAvailabilityEnum.ATTENTION,
            "TENANT_STORAGE_NOT_READY", observedAt);
      }
      Optional<TenantStorageRegistryEntity> registry = registryRepository.findByTenantId(tenant.get().getId());
      if (registry.isEmpty()) {
        return snapshot(true, true, false, TenantStorageAvailabilityEnum.WAITING,
            "TENANT_STORAGE_NOT_READY", observedAt);
      }
      return snapshot(registry.get(), observedAt);
    } catch (RuntimeException exception) {
      return snapshot(false, false, false, TenantStorageAvailabilityEnum.ATTENTION,
          "TENANT_STORAGE_UNAVAILABLE", observedAt);
    }
  }

  private TenantStorageReadinessSnapshotVO snapshot(TenantStorageRegistryEntity registry, Instant observedAt) {
    TenantStorageState storageState = registry.getStorageState();
    if (storageState == TenantStorageState.READY
        && Objects.equals(registry.getExpectedVersion(), registry.getObservedVersion())) {
      return snapshot(true, true, true, TenantStorageAvailabilityEnum.READY, null, observedAt);
    }
    if (storageState == TenantStorageState.READY) {
      return snapshot(true, true, false, TenantStorageAvailabilityEnum.ATTENTION,
          "TENANT_STORAGE_INCOMPATIBLE", observedAt);
    }
    return switch (storageState) {
      case REQUESTED, PROVISIONING, INITIALIZING -> snapshot(true, true, false,
          TenantStorageAvailabilityEnum.WAITING, "TENANT_STORAGE_NOT_READY", observedAt);
      case MIGRATING -> snapshot(true, true, false, TenantStorageAvailabilityEnum.MIGRATING,
          "TENANT_STORAGE_NOT_READY", observedAt);
      case INACTIVE -> snapshot(true, true, false, TenantStorageAvailabilityEnum.INACTIVE,
          "TENANT_STORAGE_NOT_READY", observedAt);
      case FAILED, QUARANTINED, DEACTIVATING -> snapshot(true, true, false,
          TenantStorageAvailabilityEnum.ATTENTION, "TENANT_STORAGE_NOT_READY", observedAt);
      case READY -> throw new IllegalStateException("READY state must have been handled before availability mapping");
    };
  }

  private static TenantStorageReadinessSnapshotVO snapshot(boolean sourceAvailable, boolean tenantKnown,
      boolean ready, TenantStorageAvailabilityEnum availability, String safeReasonCode, Instant observedAt) {
    return new TenantStorageReadinessSnapshotVO(sourceAvailable, tenantKnown, ready, availability,
        safeReasonCode, observedAt);
  }
}
