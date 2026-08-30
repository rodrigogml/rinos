package br.com.rinos.app.backend.module.storage.service;

import java.time.Clock;
import java.time.Instant;
import java.sql.SQLTransientException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.storage.component.TenantSchemaInitializer;
import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.config.StoragePropertiesConfig;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/**
 * Executa o provisionamento físico com confirmações duráveis independentes no catálogo global.
 *
 * <p>DDL, init e leitura da versão não participam de uma transação distribuída. Antes de qualquer efeito o executor
 * persiste a etapa; depois dele, relê a operação sob lock, prova que ainda possui o lease e só então confirma o
 * resultado. Uma queda ou perda de resposta deixa o efeito físico para ser observado com segurança pela tentativa
 * seguinte, sem recriar a identidade física nem repetir o init de dados estáveis.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageProvisioningOperationExecutor implements StorageOperationExecutionPort {
  private static final String SYSTEM_ORIGIN = "tenant-storage-worker";

  private final StorageOperationRepository operationRepository;
  private final StorageOperationStepRepository stepRepository;
  private final TenantStorageRegistryRepository registryRepository;
  private final StorageStateTransitionRepository transitionRepository;
  private final StorageAuditEventRepository auditRepository;
  private final StorageOperationStateTransitionService operationTransitions;
  private final StorageOperationStepStateTransitionService stepTransitions;
  private final TenantStorageStateTransitionService registryTransitions;
  private final TenantSchemaInitializer schemaInitializer;
  private final StoragePropertiesConfig storageProperties;
  private final TransactionTemplate transactions;
  private final Clock clock;

  /**
   * Cria o executor de provisionamento com transações exclusivamente no schema global.
   *
   * @param operationRepository fila estrutural durável
   * @param stepRepository etapas duráveis e únicas da operação
   * @param registryRepository registro global do tenant físico
   * @param transitionRepository histórico append-only das transições estruturais
   * @param auditRepository eventos operacionais minimizados
   * @param operationTransitions validador da máquina de estados da fila
   * @param stepTransitions validador da máquina de estados das etapas
   * @param registryTransitions validador da máquina de estados do tenant
   * @param schemaInitializer fronteira não transacional de criação, init e validação física
   * @param transactionManager gerenciador do schema global
   */
  @Autowired
  public TenantStorageProvisioningOperationExecutor(StorageOperationRepository operationRepository,
      StorageOperationStepRepository stepRepository, TenantStorageRegistryRepository registryRepository,
      StorageStateTransitionRepository transitionRepository, StorageAuditEventRepository auditRepository,
      StorageOperationStateTransitionService operationTransitions,
      StorageOperationStepStateTransitionService stepTransitions,
      TenantStorageStateTransitionService registryTransitions, TenantSchemaInitializer schemaInitializer,
      StoragePropertiesConfig storageProperties, PlatformTransactionManager transactionManager) {
    this(operationRepository, stepRepository, registryRepository, transitionRepository, auditRepository,
        operationTransitions, stepTransitions, registryTransitions, schemaInitializer, storageProperties, transactionManager,
        Clock.systemUTC());
  }

  TenantStorageProvisioningOperationExecutor(StorageOperationRepository operationRepository,
      StorageOperationStepRepository stepRepository, TenantStorageRegistryRepository registryRepository,
      StorageStateTransitionRepository transitionRepository, StorageAuditEventRepository auditRepository,
      StorageOperationStateTransitionService operationTransitions,
      StorageOperationStepStateTransitionService stepTransitions,
      TenantStorageStateTransitionService registryTransitions, TenantSchemaInitializer schemaInitializer,
      StoragePropertiesConfig storageProperties, PlatformTransactionManager transactionManager, Clock clock) {
    this.operationRepository = Objects.requireNonNull(operationRepository, "operationRepository must not be null");
    this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
    this.registryRepository = Objects.requireNonNull(registryRepository, "registryRepository must not be null");
    this.transitionRepository = Objects.requireNonNull(transitionRepository, "transitionRepository must not be null");
    this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
    this.operationTransitions = Objects.requireNonNull(operationTransitions, "operationTransitions must not be null");
    this.stepTransitions = Objects.requireNonNull(stepTransitions, "stepTransitions must not be null");
    this.registryTransitions = Objects.requireNonNull(registryTransitions, "registryTransitions must not be null");
    this.schemaInitializer = Objects.requireNonNull(schemaInitializer, "schemaInitializer must not be null");
    this.storageProperties = Objects.requireNonNull(storageProperties, "storageProperties must not be null");
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
        "transactionManager must not be null"));
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Executa apenas uma operação {@link StorageOperationType#PROVISION} que ainda pertence ao lease informado.
   *
   * <p>Quando a confirmação do efeito físico não puder ser gravada, nenhuma promoção é feita. A próxima tentativa
   * consulta o schema existente e valida sua versão antes de concluir, tornando a recuperação segura após perda de
   * resposta.</p>
   *
   * @param claim posse temporária emitida pela fila global
   */
  @Override
  public void execute(StorageOperationClaimVO claim) {
    Objects.requireNonNull(claim, "claim must not be null");
    if (claim.operationType() != StorageOperationType.PROVISION) {
      return;
    }

    ProvisioningContext context = executeInGlobalTransaction(() -> begin(claim));
    if (context == null) {
      return;
    }

    boolean prepared = executeInGlobalTransaction(() -> preparePhysicalExecution(claim));
    if (!prepared) {
      return;
    }

    try {
      schemaInitializer.initialize(context.physicalIdentifier(), context.expectedVersion());
    } catch (RuntimeException exception) {
      executeInGlobalTransaction(() -> {
        handlePhysicalFailure(claim, exception);
        return null;
      });
      return;
    }

    boolean confirmed = executeInGlobalTransaction(() -> confirmPhysicalExecution(claim));
    if (!confirmed) {
      return;
    }
    executeInGlobalTransaction(() -> {
      confirmReadiness(claim);
      return null;
    });
  }

  /**
   * Declara a responsabilidade exclusiva pelo provisionamento inicial de um tenant.
   *
   * @param operationType tipo consultado pelo despachante estrutural
   * @return {@code true} somente para {@link StorageOperationType#PROVISION}
   */
  @Override
  public boolean supports(StorageOperationType operationType) {
    return operationType == StorageOperationType.PROVISION;
  }

  private ProvisioningContext begin(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null) {
      return null;
    }
    Instant now = clock.instant();
    StorageOperationEntity operation = record.operation();
    if (operation.getOperationState() == StorageOperationState.CLAIMED) {
      operationTransitions.transition(operation.getOperationState(), StorageOperationState.RUNNING);
      operation.start(now);
      operationRepository.saveAndFlush(operation);
    }
    completeReservation(record, now);
    moveRegistry(record, TenantStorageState.PROVISIONING, StorageOperationStepType.RESERVE,
        "PROVISIONING_STARTED", now);
    return new ProvisioningContext(record.registry().getPhysicalIdentifier(), record.registry().getExpectedVersion());
  }

  private boolean preparePhysicalExecution(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null) {
      return false;
    }
    Instant now = clock.instant();
    if (record.registry().getStorageState() == TenantStorageState.PROVISIONING) {
      moveRegistry(record, TenantStorageState.INITIALIZING, StorageOperationStepType.CREATE_SCHEMA,
          "INITIALIZATION_STARTED", now);
    }
    startStep(record.operation(), StorageOperationStepType.CREATE_SCHEMA, now);
    startStep(record.operation(), StorageOperationStepType.INITIALIZE, now);
    startStep(record.operation(), StorageOperationStepType.VERIFY_VERSION, now);
    return true;
  }

  private boolean confirmPhysicalExecution(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null) {
      return false;
    }
    Instant now = clock.instant();
    completeStep(record.operation(), StorageOperationStepType.CREATE_SCHEMA, now);
    completeStep(record.operation(), StorageOperationStepType.INITIALIZE, now);
    completeStep(record.operation(), StorageOperationStepType.VERIFY_VERSION, now);
    if (record.registry().getStorageState() == TenantStorageState.INITIALIZING) {
      moveRegistry(record, TenantStorageState.MIGRATING, StorageOperationStepType.VERIFY_VERSION,
          "STRUCTURE_VALIDATED", now);
    }
    return true;
  }

  private void confirmReadiness(StorageOperationClaimVO claim) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null) {
      return;
    }
    Instant now = clock.instant();
    startStep(record.operation(), StorageOperationStepType.VALIDATE_READINESS, now);
    completeStep(record.operation(), StorageOperationStepType.VALIDATE_READINESS, now);
    if (record.registry().getStorageState() == TenantStorageState.MIGRATING) {
      moveRegistry(record, TenantStorageState.READY, StorageOperationStepType.VALIDATE_READINESS,
          "READINESS_VALIDATED", now);
    }
    record.registry().confirmValidatedVersion(record.registry().getExpectedVersion(), now);
    registryRepository.saveAndFlush(record.registry());
    operationTransitions.transition(record.operation().getOperationState(), StorageOperationState.COMPLETED);
    record.operation().complete(now);
    operationRepository.saveAndFlush(record.operation());
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_PROVISIONING_COMPLETED",
        record.registry().getId(), record.operation().getId(), null, SYSTEM_ORIGIN,
        record.operation().getCorrelationId(), "READY", null, now));
  }

  private void handlePhysicalFailure(StorageOperationClaimVO claim, RuntimeException exception) {
    ExecutionRecord record = findOwnedRecord(claim);
    if (record == null || record.operation().getOperationState() != StorageOperationState.RUNNING) {
      return;
    }
    Instant now = clock.instant();
    FailureDisposition disposition = classify(exception);
    if (disposition.retryable() && record.operation().getAttemptCount() < storageProperties.provisioningMaximumAttempts()) {
      operationTransitions.transition(StorageOperationState.RUNNING, StorageOperationState.RETRY_WAIT);
      record.operation().scheduleRetry(now.plus(storageProperties.queuePollInterval()), disposition.safeCode());
      operationRepository.saveAndFlush(record.operation());
      auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_PROVISIONING_RETRY_SCHEDULED",
          record.registry().getId(), record.operation().getId(), null, SYSTEM_ORIGIN,
          record.operation().getCorrelationId(), disposition.safeCode(), null, now));
      return;
    }

    failRunningSteps(record.operation(), disposition.safeCode(), now);
    operationTransitions.transition(StorageOperationState.RUNNING, StorageOperationState.FAILED_FINAL);
    record.operation().failFinal(now, disposition.safeCode());
    operationRepository.saveAndFlush(record.operation());
    quarantineRegistry(record, disposition.safeCode(), now);
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_PROVISIONING_ATTENTION",
        record.registry().getId(), record.operation().getId(), null, SYSTEM_ORIGIN,
        record.operation().getCorrelationId(), disposition.safeCode(), null, now));
  }

  private void failRunningSteps(StorageOperationEntity operation, String safeFailureCode, Instant now) {
    for (StorageOperationStepType type : new StorageOperationStepType[] {StorageOperationStepType.CREATE_SCHEMA,
        StorageOperationStepType.INITIALIZE, StorageOperationStepType.VERIFY_VERSION}) {
      Optional<StorageOperationStepEntity> step = stepRepository.findByStorageOperationIdAndStepType(operation.getId(), type);
      if (step.isPresent() && step.get().getStepState() == StorageOperationStepState.RUNNING) {
        stepTransitions.transition(StorageOperationStepState.RUNNING, StorageOperationStepState.FAILED);
        step.get().fail(now, safeFailureCode);
        stepRepository.saveAndFlush(step.get());
      }
    }
  }

  private void quarantineRegistry(ExecutionRecord record, String safeFailureCode, Instant now) {
    TenantStorageState current = record.registry().getStorageState();
    if (current != TenantStorageState.QUARANTINED) {
      registryTransitions.transition(current, TenantStorageState.QUARANTINED, StorageTransitionOriginType.SYSTEM);
      record.registry().changeState(TenantStorageState.QUARANTINED);
      transitionRepository.save(new StorageStateTransitionEntity(record.registry().getId(), record.operation().getId(),
          current, TenantStorageState.QUARANTINED, StorageOperationStepType.VERIFY_VERSION,
          StorageTransitionOriginType.SYSTEM, null, SYSTEM_ORIGIN, record.operation().getCorrelationId(),
          safeFailureCode, now));
    }
    record.registry().quarantine(safeFailureCode);
    registryRepository.saveAndFlush(record.registry());
  }

  private static FailureDisposition classify(RuntimeException exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof TransientDataAccessException || current instanceof DataAccessResourceFailureException
          || current instanceof SQLTransientException) {
        return new FailureDisposition(true, "TENANT_STORAGE_TRANSIENT_FAILURE");
      }
      if (current instanceof RFWDatabaseUpdateException rfwException) {
        if (rfwException.getCategory() == RFWDatabaseUpdateErrorCategoryEnum.LOCK_TIMEOUT) {
          return new FailureDisposition(true, "TENANT_STORAGE_LOCK_TIMEOUT");
        }
        if (rfwException.getCategory() != RFWDatabaseUpdateErrorCategoryEnum.EXECUTION) {
          return new FailureDisposition(false, "TENANT_STORAGE_REQUIRES_INFRASTRUCTURE");
        }
      }
    }
    return new FailureDisposition(false, "TENANT_STORAGE_REQUIRES_INFRASTRUCTURE");
  }

  private ExecutionRecord findOwnedRecord(StorageOperationClaimVO claim) {
    Optional<StorageOperationEntity> current = operationRepository.findByPublicIdForUpdate(claim.operationPublicId());
    if (current.isEmpty()) {
      return null;
    }
    StorageOperationEntity operation = current.get();
    Instant now = clock.instant();
    if (operation.getOperationType() != StorageOperationType.PROVISION
        || !Objects.equals(operation.getTenantStorageRegistryId(), claim.registryId())
        || !operation.hasActiveLease(claim.leaseOwner(), now)) {
      return null;
    }
    TenantStorageRegistryEntity registry = registryRepository.findById(operation.getTenantStorageRegistryId())
        .orElse(null);
    if (registry == null) {
      return null;
    }
    return new ExecutionRecord(operation, registry);
  }

  private void completeReservation(ExecutionRecord record, Instant now) {
    StorageOperationStepEntity reserve = step(record.operation(), StorageOperationStepType.RESERVE);
    if (reserve.getStepState() == StorageOperationStepState.PENDING) {
      stepTransitions.transition(reserve.getStepState(), StorageOperationStepState.RUNNING);
      reserve.start(now);
    }
    if (reserve.getStepState() == StorageOperationStepState.RUNNING) {
      stepTransitions.transition(reserve.getStepState(), StorageOperationStepState.COMPLETED);
      reserve.complete(now);
      stepRepository.saveAndFlush(reserve);
    }
  }

  private void startStep(StorageOperationEntity operation, StorageOperationStepType type, Instant now) {
    StorageOperationStepEntity step = step(operation, type);
    if (step.getStepState() == StorageOperationStepState.PENDING) {
      stepTransitions.transition(step.getStepState(), StorageOperationStepState.RUNNING);
      step.start(now);
      stepRepository.saveAndFlush(step);
    }
  }

  private void completeStep(StorageOperationEntity operation, StorageOperationStepType type, Instant now) {
    StorageOperationStepEntity step = step(operation, type);
    if (step.getStepState() == StorageOperationStepState.PENDING) {
      stepTransitions.transition(step.getStepState(), StorageOperationStepState.RUNNING);
      step.start(now);
    }
    if (step.getStepState() == StorageOperationStepState.RUNNING) {
      stepTransitions.transition(step.getStepState(), StorageOperationStepState.COMPLETED);
      step.complete(now);
      stepRepository.saveAndFlush(step);
    }
  }

  private StorageOperationStepEntity step(StorageOperationEntity operation, StorageOperationStepType type) {
    return stepRepository.findByStorageOperationIdAndStepType(operation.getId(), type)
        .orElseGet(() -> stepRepository.saveAndFlush(new StorageOperationStepEntity(operation.getId(), type)));
  }

  private void moveRegistry(ExecutionRecord record, TenantStorageState target,
      StorageOperationStepType stepType, String safeResultCode, Instant now) {
    TenantStorageState current = record.registry().getStorageState();
    if (current == target) {
      return;
    }
    registryTransitions.transition(current, target, StorageTransitionOriginType.SYSTEM);
    record.registry().changeState(target);
    registryRepository.saveAndFlush(record.registry());
    transitionRepository.save(new StorageStateTransitionEntity(record.registry().getId(), record.operation().getId(),
        current, target, stepType, StorageTransitionOriginType.SYSTEM, null, SYSTEM_ORIGIN,
        record.operation().getCorrelationId(), safeResultCode, now));
    auditRepository.save(new StorageAuditEventEntity("TENANT_STORAGE_STATE_CHANGED", record.registry().getId(),
        record.operation().getId(), null, SYSTEM_ORIGIN, record.operation().getCorrelationId(), safeResultCode,
        null, now));
  }

  private <T> T executeInGlobalTransaction(GlobalWork<T> work) {
    T result = transactions.execute(status -> work.execute());
    return result;
  }

  private record ExecutionRecord(StorageOperationEntity operation, TenantStorageRegistryEntity registry) {
  }

  private record ProvisioningContext(TenantPhysicalIdentifier physicalIdentifier, String expectedVersion) {
  }

  private record FailureDisposition(boolean retryable, String safeCode) {
  }

  @FunctionalInterface
  private interface GlobalWork<T> {
    T execute();
  }
}
