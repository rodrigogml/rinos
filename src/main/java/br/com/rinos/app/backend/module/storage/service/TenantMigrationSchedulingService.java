package br.com.rinos.app.backend.module.storage.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;

/**
 * Enfileira migrations de tenants prontos depois que o catálogo global já concluiu o startup.
 *
 * <p>A chave de idempotência é derivada do registro físico e da versão alvo do catálogo tenant. Assim, startups
 * concorrentes ou repetidos convergem para uma única operação por tenant e versão, sem transformar a fila em um
 * mecanismo de repetição de migrations falhas.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantMigrationSchedulingService {

  private static final String SYSTEM_ORIGIN = "tenant-migration-startup";
  private static final String SYSTEM_CORRELATION_PREFIX = "tenant-migration-startup-";

  private final TenantStorageRegistryRepository registryRepository;
  private final StorageOperationRepository operationRepository;
  private final StorageStateTransitionRepository transitionRepository;
  private final StorageAuditEventRepository auditRepository;
  private final TenantDatabaseCatalogService catalogService;
  private final TenantStorageStateTransitionService registryTransitions;
  private final Clock clock;

  /**
   * Cria o agendador durável usando somente os catálogos e repositórios globais de storage.
   *
   * @param registryRepository inventário físico global dos tenants
   * @param operationRepository fila global com unicidade de idempotência
   * @param transitionRepository histórico append-only da mudança para migrando
   * @param auditRepository evento operacional minimizado da indisponibilidade planejada
   * @param catalogService catálogo distribuído que informa a versão alvo do tenant
   * @param registryTransitions validador da máquina de estados do tenant
   */
  public TenantMigrationSchedulingService(TenantStorageRegistryRepository registryRepository,
      StorageOperationRepository operationRepository, StorageStateTransitionRepository transitionRepository,
      StorageAuditEventRepository auditRepository, TenantDatabaseCatalogService catalogService,
      TenantStorageStateTransitionService registryTransitions) {
    this(registryRepository, operationRepository, transitionRepository, auditRepository, catalogService,
        registryTransitions, Clock.systemUTC());
  }

  TenantMigrationSchedulingService(TenantStorageRegistryRepository registryRepository,
      StorageOperationRepository operationRepository, StorageStateTransitionRepository transitionRepository,
      StorageAuditEventRepository auditRepository, TenantDatabaseCatalogService catalogService,
      TenantStorageStateTransitionService registryTransitions, Clock clock) {
    this.registryRepository = Objects.requireNonNull(registryRepository, "registryRepository must not be null");
    this.operationRepository = Objects.requireNonNull(operationRepository, "operationRepository must not be null");
    this.transitionRepository = Objects.requireNonNull(transitionRepository, "transitionRepository must not be null");
    this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
    this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
    this.registryTransitions = Objects.requireNonNull(registryTransitions, "registryTransitions must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Enfileira uma migration para cada tenant pronto cuja versão esperada difere do catálogo atual.
   *
   * @return quantidade de novas operações aceitas nesta execução
   */
  @Transactional
  public int schedulePendingMigrations() {
    String targetVersion = catalogService.inspect().targetVersion().value();
    int scheduled = 0;
    for (TenantStorageRegistryEntity registry : registryRepository.findAllReadyForMigrationScheduling()) {
      if (targetVersion.equals(registry.getExpectedVersion())) {
        continue;
      }
      schedule(registry, targetVersion, clock.instant());
      scheduled++;
    }
    return scheduled;
  }

  /**
   * Cria a intenção de migration e bloqueia o tenant antes que a aplicação aceite seu contexto funcional.
   *
   * @param registry tenant pronto, protegido pelo lock pessimista da seleção
   * @param targetVersion versão exigida pelo artefato atualmente inicializado
   * @param now instante UTC único da transação de agendamento
   */
  private void schedule(TenantStorageRegistryEntity registry, String targetVersion, Instant now) {
    Long registryId = Objects.requireNonNull(registry.getId(), "registry id must not be null");
    StorageOperationEntity operation = operationRepository.saveAndFlush(new StorageOperationEntity(UUID.randomUUID(),
        registryId, StorageOperationType.MIGRATE, migrationReference(registryId, targetVersion),
        SYSTEM_CORRELATION_PREFIX + targetVersion));
    registryTransitions.transition(TenantStorageState.READY, TenantStorageState.MIGRATING,
        StorageTransitionOriginType.SYSTEM);
    registry.expectVersion(targetVersion);
    registry.changeState(TenantStorageState.MIGRATING);
    registryRepository.saveAndFlush(registry);
    transitionRepository.save(new StorageStateTransitionEntity(registryId, operation.getId(), TenantStorageState.READY,
        TenantStorageState.MIGRATING, StorageOperationStepType.MIGRATE, StorageTransitionOriginType.SYSTEM, null,
        SYSTEM_ORIGIN, operation.getCorrelationId(), "TENANT_MIGRATION_QUEUED", now));
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_MIGRATION_QUEUED", registryId,
        operation.getId(), null, SYSTEM_ORIGIN, operation.getCorrelationId(), "MIGRATING", null, now));
  }

  /**
   * Produz a chave estável da única intenção de migration possível para um tenant e versão de destino.
   *
   * @param registryId identificador interno do inventário de tenant
   * @param targetVersion versão estrutural solicitada
   * @return UUID determinístico de idempotência
   */
  private static UUID migrationReference(Long registryId, String targetVersion) {
    return UUID.nameUUIDFromBytes(("tenant-migration:" + registryId + ":" + targetVersion)
        .getBytes(StandardCharsets.UTF_8));
  }
}
