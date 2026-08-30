package br.com.rinos.app.backend.module.storage.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.component.TenantDataSourceFactory;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseStructureVerifier;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseUpdateRequestFactory;
import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageMigrationExecutionState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageMigrationExecutionRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseMigrationEvidenceVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseUpdateScriptVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateOrchestratorService;
import br.eng.rodrigogml.rfw.database.service.DatabaseVersionService;
import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

/**
 * Executa uma migration de tenant isolada, auditável e sem repetição automática após falha confirmada.
 *
 * <p>O worker grava a intenção e as evidências de scripts no global antes de delegar DDL à RFW. A parte física não
 * participa da transação global; portanto, toda confirmação posterior reabre a operação sob lease. Uma exceção de
 * migration finaliza a operação, preserva a evidência disponível e coloca somente o tenant correspondente em
 * quarentena. Não há rollback nem agendamento de retry para migrations.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageMigrationOperationExecutor implements StorageOperationExecutionPort {

  private static final String SYSTEM_ORIGIN = "tenant-storage-worker";
  private static final String FAILURE_CODE = "TENANT_MIGRATION_REQUIRES_INFRASTRUCTURE";

  private final StorageOperationRepository operationRepository;
  private final StorageOperationStepRepository stepRepository;
  private final TenantStorageRegistryRepository registryRepository;
  private final StorageMigrationExecutionRepository migrationRepository;
  private final StorageStateTransitionRepository transitionRepository;
  private final StorageAuditEventRepository auditRepository;
  private final StorageOperationStateTransitionService operationTransitions;
  private final StorageOperationStepStateTransitionService stepTransitions;
  private final TenantStorageStateTransitionService registryTransitions;
  private final TenantDataSourceFactory dataSourceFactory;
  private final TenantDatabaseUpdateRequestFactory updateRequestFactory;
  private final TenantDatabaseCatalogService catalogService;
  private final TenantDatabaseStructureVerifier structureVerifier;
  private final DatabaseVersionService versionService;
  private final DatabaseUpdateOrchestratorService updateOrchestrator;
  private final TransactionTemplate transactions;
  private final Clock clock;

  /**
   * Cria o executor das migrations físicas de tenant usando somente transações do schema global.
   *
   * @param operationRepository fila estrutural global
   * @param stepRepository etapas duráveis da operação
   * @param registryRepository inventário global de tenants
   * @param migrationRepository histórico de scripts aplicados ou falhos
   * @param transitionRepository histórico append-only de estados
   * @param auditRepository auditoria operacional minimizada
   * @param operationTransitions validador do ciclo da fila
   * @param stepTransitions validador do ciclo da etapa de migration
   * @param registryTransitions validador do ciclo do tenant
   * @param dataSourceFactory fábrica isolada de datasource de tenant
   * @param updateRequestFactory montagem isolada da requisição RFW
   * @param catalogService catálogo oficial de scripts de tenant
   * @param structureVerifier validador físico posterior ao DDL
   * @param versionService leitura RFW da versão física anterior
   * @param updateOrchestrator orquestrador RFW que executa o DDL isolado
   * @param transactionManager gerenciador transacional do schema global
   */
  @Autowired
  public TenantStorageMigrationOperationExecutor(StorageOperationRepository operationRepository,
      StorageOperationStepRepository stepRepository, TenantStorageRegistryRepository registryRepository,
      StorageMigrationExecutionRepository migrationRepository, StorageStateTransitionRepository transitionRepository,
      StorageAuditEventRepository auditRepository, StorageOperationStateTransitionService operationTransitions,
      StorageOperationStepStateTransitionService stepTransitions, TenantStorageStateTransitionService registryTransitions,
      TenantDataSourceFactory dataSourceFactory, TenantDatabaseUpdateRequestFactory updateRequestFactory,
      TenantDatabaseCatalogService catalogService, TenantDatabaseStructureVerifier structureVerifier,
      DatabaseVersionService versionService, DatabaseUpdateOrchestratorService updateOrchestrator,
      PlatformTransactionManager transactionManager) {
    this(operationRepository, stepRepository, registryRepository, migrationRepository, transitionRepository,
        auditRepository, operationTransitions, stepTransitions, registryTransitions, dataSourceFactory,
        updateRequestFactory, catalogService, structureVerifier, versionService, updateOrchestrator,
        transactionManager, Clock.systemUTC());
  }

  TenantStorageMigrationOperationExecutor(StorageOperationRepository operationRepository,
      StorageOperationStepRepository stepRepository, TenantStorageRegistryRepository registryRepository,
      StorageMigrationExecutionRepository migrationRepository, StorageStateTransitionRepository transitionRepository,
      StorageAuditEventRepository auditRepository, StorageOperationStateTransitionService operationTransitions,
      StorageOperationStepStateTransitionService stepTransitions, TenantStorageStateTransitionService registryTransitions,
      TenantDataSourceFactory dataSourceFactory, TenantDatabaseUpdateRequestFactory updateRequestFactory,
      TenantDatabaseCatalogService catalogService, TenantDatabaseStructureVerifier structureVerifier,
      DatabaseVersionService versionService, DatabaseUpdateOrchestratorService updateOrchestrator,
      PlatformTransactionManager transactionManager, Clock clock) {
    this.operationRepository = Objects.requireNonNull(operationRepository, "operationRepository must not be null");
    this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
    this.registryRepository = Objects.requireNonNull(registryRepository, "registryRepository must not be null");
    this.migrationRepository = Objects.requireNonNull(migrationRepository, "migrationRepository must not be null");
    this.transitionRepository = Objects.requireNonNull(transitionRepository, "transitionRepository must not be null");
    this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
    this.operationTransitions = Objects.requireNonNull(operationTransitions, "operationTransitions must not be null");
    this.stepTransitions = Objects.requireNonNull(stepTransitions, "stepTransitions must not be null");
    this.registryTransitions = Objects.requireNonNull(registryTransitions, "registryTransitions must not be null");
    this.dataSourceFactory = Objects.requireNonNull(dataSourceFactory, "dataSourceFactory must not be null");
    this.updateRequestFactory = Objects.requireNonNull(updateRequestFactory, "updateRequestFactory must not be null");
    this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
    this.structureVerifier = Objects.requireNonNull(structureVerifier, "structureVerifier must not be null");
    this.versionService = Objects.requireNonNull(versionService, "versionService must not be null");
    this.updateOrchestrator = Objects.requireNonNull(updateOrchestrator, "updateOrchestrator must not be null");
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
        "transactionManager must not be null"));
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Executa a operação de migration ainda pertencente ao lease informado.
   *
   * @param claim posse temporária emitida pela fila global
   */
  @Override
  public void execute(StorageOperationClaimVO claim) {
    Objects.requireNonNull(claim, "claim must not be null");
    if (claim.operationType() != StorageOperationType.MIGRATE) {
      return;
    }

    MigrationContext context = global(() -> begin(claim));
    if (context == null) {
      return;
    }

    try (HikariDataSource tenantDataSource = dataSourceFactory.create(context.physicalIdentifier())) {
      PhysicalMigrationPlan plan = discoverPlan(tenantDataSource);
      if (!global(() -> prepare(claim, plan))) {
        return;
      }
      updateOrchestrator.updateDatabase(updateRequestFactory.create(tenantDataSource));
      Collection<TenantDatabaseMigrationEvidenceVO> evidence = global(() -> evidenceFor(claim));
      structureVerifier.verify(tenantDataSource, context.expectedVersion(), evidence);
      global(() -> {
        confirm(claim, plan);
        return null;
      });
    } catch (RuntimeException exception) {
      global(() -> {
        fail(claim);
        return null;
      });
    }
  }

  /**
   * Declara a responsabilidade exclusiva por operações de atualização de tenant.
   *
   * @param operationType tipo consultado pelo despachante estrutural
   * @return {@code true} somente para {@link StorageOperationType#MIGRATE}
   */
  @Override
  public boolean supports(StorageOperationType operationType) {
    return operationType == StorageOperationType.MIGRATE;
  }

  /**
   * Marca a operação como em execução e confirma que o tenant continua indisponível para uso funcional.
   *
   * @param claim lease atual do worker
   * @return contexto físico ou {@code null} quando a posse não for mais válida
   */
  private MigrationContext begin(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null) {
      return null;
    }
    if (record.operation().getOperationState() == StorageOperationState.CLAIMED) {
      operationTransitions.transition(StorageOperationState.CLAIMED, StorageOperationState.RUNNING);
      record.operation().start(clock.instant());
      operationRepository.saveAndFlush(record.operation());
    }
    return new MigrationContext(record.registry().getPhysicalIdentifier(), record.registry().getExpectedVersion());
  }

  /**
   * Lê a versão física e o catálogo para registrar antecipadamente os scripts que a RFW poderá aplicar.
   *
   * @param tenantDataSource datasource exclusivo do tenant já bloqueado para uso funcional
   * @return fotografia do plano físico antes do DDL
   */
  private PhysicalMigrationPlan discoverPlan(HikariDataSource tenantDataSource) {
    TenantDatabaseCatalogVO catalog = catalogService.inspect();
    DatabaseVersionVO previousVersion = versionService.readCurrentVersion(tenantDataSource);
    List<TenantDatabaseUpdateScriptVO> pendingScripts = catalog.scripts().stream()
        .filter(script -> script.version().compareTo(previousVersion) > 0)
        .toList();
    return new PhysicalMigrationPlan(previousVersion.value(), pendingScripts);
  }

  /**
   * Persiste a etapa e as evidências antes de delegar qualquer DDL ao orquestrador RFW.
   *
   * @param claim lease atual do worker
   * @param plan fotografia dos scripts pendentes
   * @return {@code true} quando a posse ainda permite o efeito físico
   */
  private boolean prepare(StorageOperationClaimVO claim, PhysicalMigrationPlan plan) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null || record.operation().getOperationState() != StorageOperationState.RUNNING) {
      return false;
    }
    Instant now = clock.instant();
    startStep(record.operation(), now);
    for (TenantDatabaseUpdateScriptVO script : plan.pendingScripts()) {
      if (migrationRepository.findByTenantStorageRegistryIdAndScriptVersion(record.registry().getId(),
          script.version().value()).isEmpty()) {
        migrationRepository.saveAndFlush(new StorageMigrationExecutionEntity(record.registry().getId(),
            record.operation().getId(), script.version().value(), script.fileName(), script.contentHash(),
            plan.previousVersion(), now));
      }
    }
    return true;
  }

  /**
   * Constrói as evidências que a verificação estrutural aceita para o tenant em migration.
   *
   * @param claim lease atual do worker
   * @return evidências válidas, incluindo scripts iniciados por esta mesma operação
   */
  private Collection<TenantDatabaseMigrationEvidenceVO> evidenceFor(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null) {
      throw new IllegalStateException("tenant migration lease is no longer owned");
    }
    return migrationRepository.findAllByTenantStorageRegistryIdOrderByScriptVersion(record.registry().getId()).stream()
        .filter(execution -> execution.getExecutionState() != StorageMigrationExecutionState.FAILED)
        .map(execution -> new TenantDatabaseMigrationEvidenceVO(execution.getScriptName(),
            new DatabaseVersionVO(execution.getScriptVersion()), execution.getScriptHash()))
        .toList();
  }

  /**
   * Confirma scripts, versão e prontidão somente após a verificação física bem-sucedida.
   *
   * @param claim lease atual do worker
   * @param plan fotografia do plano executado
   */
  private void confirm(StorageOperationClaimVO claim, PhysicalMigrationPlan plan) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null || record.operation().getOperationState() != StorageOperationState.RUNNING) {
      return;
    }
    Instant now = clock.instant();
    for (StorageMigrationExecutionEntity execution : migrationRepository
        .findAllByStorageOperationIdOrderByScriptVersion(record.operation().getId())) {
      if (execution.getExecutionState() == StorageMigrationExecutionState.STARTED) {
        execution.complete(execution.getScriptVersion(), now);
        migrationRepository.saveAndFlush(execution);
      }
    }
    completeStep(record.operation(), now);
    record.registry().confirmValidatedVersion(record.registry().getExpectedVersion(), now);
    moveRegistry(record, TenantStorageState.READY, "TENANT_MIGRATION_COMPLETED", now);
    registryRepository.saveAndFlush(record.registry());
    operationTransitions.transition(StorageOperationState.RUNNING, StorageOperationState.COMPLETED);
    record.operation().complete(now);
    operationRepository.saveAndFlush(record.operation());
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_MIGRATION_COMPLETED", record.registry().getId(),
        record.operation().getId(), null, SYSTEM_ORIGIN, record.operation().getCorrelationId(), "READY", null, now));
  }

  /**
   * Fecha definitivamente a migration, sem retry ou rollback, e isola somente o tenant afetado.
   *
   * @param claim lease atual do worker
   */
  private void fail(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null || record.operation().getOperationState() != StorageOperationState.RUNNING) {
      return;
    }
    Instant now = clock.instant();
    String safeCode = failureCode();
    failStep(record.operation(), safeCode, now);
    for (StorageMigrationExecutionEntity execution : migrationRepository
        .findAllByStorageOperationIdOrderByScriptVersion(record.operation().getId())) {
      if (execution.getExecutionState() == StorageMigrationExecutionState.STARTED) {
        execution.fail(safeCode, now);
        migrationRepository.saveAndFlush(execution);
      }
    }
    operationTransitions.transition(StorageOperationState.RUNNING, StorageOperationState.FAILED_FINAL);
    record.operation().failFinal(now, safeCode);
    operationRepository.saveAndFlush(record.operation());
    record.registry().quarantine(safeCode);
    moveRegistry(record, TenantStorageState.QUARANTINED, safeCode, now);
    registryRepository.saveAndFlush(record.registry());
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_MIGRATION_ATTENTION", record.registry().getId(),
        record.operation().getId(), null, SYSTEM_ORIGIN, record.operation().getCorrelationId(), safeCode, null, now));
  }

  /**
   * Cria ou inicia a etapa única de migration da operação corrente.
   *
   * @param operation operação em execução
   * @param now instante UTC do início
   */
  private void startStep(StorageOperationEntity operation, Instant now) {
    StorageOperationStepEntity step = step(operation);
    if (step.getStepState() == StorageOperationStepState.PENDING) {
      stepTransitions.transition(StorageOperationStepState.PENDING, StorageOperationStepState.RUNNING);
      step.start(now);
      stepRepository.saveAndFlush(step);
    }
  }

  /**
   * Confirma a etapa de migration já observada no datasource isolado.
   *
   * @param operation operação em execução
   * @param now instante UTC da conclusão
   */
  private void completeStep(StorageOperationEntity operation, Instant now) {
    StorageOperationStepEntity step = step(operation);
    if (step.getStepState() == StorageOperationStepState.RUNNING) {
      stepTransitions.transition(StorageOperationStepState.RUNNING, StorageOperationStepState.COMPLETED);
      step.complete(now);
      stepRepository.saveAndFlush(step);
    }
  }

  /**
   * Registra falha da etapa quando ela já tinha sido iniciada antes do efeito físico.
   *
   * @param operation operação em execução
   * @param safeCode código seguro da falha
   * @param now instante UTC do encerramento
   */
  private void failStep(StorageOperationEntity operation, String safeCode, Instant now) {
    Optional<StorageOperationStepEntity> current = stepRepository.findByStorageOperationIdAndStepType(
        operation.getId(), StorageOperationStepType.MIGRATE);
    if (current.isPresent() && current.get().getStepState() == StorageOperationStepState.RUNNING) {
      stepTransitions.transition(StorageOperationStepState.RUNNING, StorageOperationStepState.FAILED);
      current.get().fail(now, safeCode);
      stepRepository.saveAndFlush(current.get());
    }
  }

  /**
   * Obtém a etapa de migration, criando sua intenção durável quando ainda não existir.
   *
   * @param operation operação em execução
   * @return etapa associada somente a esta operação
   */
  private StorageOperationStepEntity step(StorageOperationEntity operation) {
    return stepRepository.findByStorageOperationIdAndStepType(operation.getId(), StorageOperationStepType.MIGRATE)
        .orElseGet(() -> stepRepository.saveAndFlush(new StorageOperationStepEntity(operation.getId(),
            StorageOperationStepType.MIGRATE)));
  }

  /**
   * Registra uma transição validada de tenant e sua evidência administrativa minimizada.
   *
   * @param record operação e tenant protegidos pelo lease
   * @param target próximo estado do tenant
   * @param safeCode resultado seguro da transição
   * @param now instante UTC da alteração
   */
  private void moveRegistry(ExecutionRecord record, TenantStorageState target, String safeCode, Instant now) {
    TenantStorageState current = record.registry().getStorageState();
    if (current == target) {
      return;
    }
    registryTransitions.transition(current, target, StorageTransitionOriginType.SYSTEM);
    record.registry().changeState(target);
    transitionRepository.save(new StorageStateTransitionEntity(record.registry().getId(), record.operation().getId(),
        current, target, StorageOperationStepType.MIGRATE, StorageTransitionOriginType.SYSTEM, null,
        SYSTEM_ORIGIN, record.operation().getCorrelationId(), safeCode, now));
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_STATE_CHANGED", record.registry().getId(),
        record.operation().getId(), null, SYSTEM_ORIGIN, record.operation().getCorrelationId(), safeCode, null, now));
  }

  /**
   * Reabre a operação sob lock e comprova que esta instância ainda é a dona da execução física.
   *
   * @param claim lease recebido pelo despachante
   * @return operação e tenant correspondentes, ou {@code null} quando a posse não for segura
   */
  private ExecutionRecord findOwnedRecord(StorageOperationClaimVO claim) {
    Optional<StorageOperationEntity> current = operationRepository.findByPublicIdForUpdate(claim.operationPublicId());
    if (current.isEmpty()) {
      return null;
    }
    StorageOperationEntity operation = current.get();
    if (operation.getOperationType() != StorageOperationType.MIGRATE
        || !Objects.equals(operation.getTenantStorageRegistryId(), claim.registryId())
        || !operation.hasActiveLease(claim.leaseOwner(), clock.instant())) {
      return null;
    }
    TenantStorageRegistryEntity registry = registryRepository.findById(operation.getTenantStorageRegistryId())
        .orElse(null);
    if (registry == null || registry.getStorageState() != TenantStorageState.MIGRATING) {
      return null;
    }
    return new ExecutionRecord(operation, registry);
  }

  /**
   * Define o único código seguro para falhas de migration que exigem intervenção de infraestrutura.
   * @return código seguro e estável para auditoria e quarentena
   */
  private static String failureCode() {
    return FAILURE_CODE;
  }

  /**
   * Executa uma unidade curta de trabalho exclusivamente na transação do catálogo global.
   *
   * @param work operação sem DDL de tenant
   * @param <T> tipo do resultado
   * @return resultado da unidade transacional
   */
  private <T> T global(GlobalWork<T> work) {
    return transactions.execute(status -> work.execute());
  }

  private record ExecutionRecord(StorageOperationEntity operation, TenantStorageRegistryEntity registry) {
  }

  private record MigrationContext(TenantPhysicalIdentifier physicalIdentifier, String expectedVersion) {
  }

  private record PhysicalMigrationPlan(String previousVersion, List<TenantDatabaseUpdateScriptVO> pendingScripts) {
  }

  @FunctionalInterface
  private interface GlobalWork<T> {
    T execute();
  }
}
