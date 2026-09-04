package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import br.com.rinos.app.backend.module.storage.component.TenantSchemaInitializer;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.vo.TenantSchemaInitializationResultVO;
import br.com.rinos.app.config.StoragePropertiesConfig;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

class TenantStorageProvisioningOperationExecutorTest {

  private static final Instant NOW = Instant.parse("2026-08-30T15:00:00Z");

  @Test
  void execute_shouldConfirmEachStageAndReadyTenant_whenLeaseIsStillOwned() {
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    StorageOperationStepRepository steps = mock(StorageOperationStepRepository.class);
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    TenantSchemaInitializer initializer = mock(TenantSchemaInitializer.class);
    StorageOperationEntity operation = operation();
    TenantStorageRegistryEntity registry = registry();
    Map<StorageOperationStepType, StorageOperationStepEntity> stepByType = new EnumMap<>(
        StorageOperationStepType.class);
    stepByType.put(StorageOperationStepType.RESERVE,
        new StorageOperationStepEntity(operation.getId(), StorageOperationStepType.RESERVE));
    configureStepRepository(steps, stepByType);
    when(operations.findByPublicIdForUpdate(operation.getPublicId())).thenReturn(Optional.of(operation));
    when(operations.saveAndFlush(any(StorageOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(registries.findById(registry.getId())).thenReturn(Optional.of(registry));
    when(registries.saveAndFlush(any(TenantStorageRegistryEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(initializer.initialize(registry.getPhysicalIdentifier(), registry.getExpectedVersion()))
        .thenReturn(new TenantSchemaInitializationResultVO(true));
    TenantStorageProvisioningOperationExecutor executor = executor(operations, steps, registries, transitions, audits,
        initializer);
    StorageOperationClaimVO claim = new StorageOperationClaimVO(operation.getPublicId(), registry.getId(),
        StorageOperationType.PROVISION, "instance-a", NOW.plusSeconds(600));

    executor.execute(claim);

    assertThat(operation.getOperationState()).isEqualTo(StorageOperationState.COMPLETED);
    assertThat(operation.getActiveMarker()).isNull();
    assertThat(operation.getLeaseOwner()).isNull();
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.READY);
    assertThat(registry.getObservedVersion()).isEqualTo(registry.getExpectedVersion());
    assertThat(registry.getLastValidatedAt()).isEqualTo(NOW);
    assertThat(stepByType).hasSize(5);
    assertThat(stepByType.values()).allMatch(step -> step.getStepState() == StorageOperationStepState.COMPLETED);
    verify(initializer).initialize(registry.getPhysicalIdentifier(), registry.getExpectedVersion());
    verify(transitions, times(4)).save(any());
    verify(audits, times(5)).save(any());
  }

  @Test
  void execute_shouldNotPerformPhysicalEffect_whenLeaseNoLongerBelongsToClaim() {
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    StorageOperationStepRepository steps = mock(StorageOperationStepRepository.class);
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    TenantSchemaInitializer initializer = mock(TenantSchemaInitializer.class);
    StorageOperationEntity operation = operation();
    TenantStorageRegistryEntity registry = registry();
    when(operations.findByPublicIdForUpdate(operation.getPublicId())).thenReturn(Optional.of(operation));
    TenantStorageProvisioningOperationExecutor executor = executor(operations, steps, registries, transitions, audits,
        initializer);
    StorageOperationClaimVO invalidClaim = new StorageOperationClaimVO(operation.getPublicId(), registry.getId(),
        StorageOperationType.PROVISION, "instance-b", NOW.plusSeconds(600));

    executor.execute(invalidClaim);

    assertThat(operation.getOperationState()).isEqualTo(StorageOperationState.CLAIMED);
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.REQUESTED);
    verify(initializer, never()).initialize(any(), any());
    verify(registries, never()).findById(any());
  }

  @Test
  void execute_shouldScheduleRetry_whenPhysicalFailureIsTransientAndAttemptsRemain() {
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    StorageOperationStepRepository steps = mock(StorageOperationStepRepository.class);
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    TenantSchemaInitializer initializer = mock(TenantSchemaInitializer.class);
    StorageOperationEntity operation = operation();
    TenantStorageRegistryEntity registry = registry();
    Map<StorageOperationStepType, StorageOperationStepEntity> stepByType = new EnumMap<>(
        StorageOperationStepType.class);
    stepByType.put(StorageOperationStepType.RESERVE,
        new StorageOperationStepEntity(operation.getId(), StorageOperationStepType.RESERVE));
    configureStepRepository(steps, stepByType);
    when(operations.findByPublicIdForUpdate(operation.getPublicId())).thenReturn(Optional.of(operation));
    when(operations.saveAndFlush(any(StorageOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(registries.findById(registry.getId())).thenReturn(Optional.of(registry));
    when(registries.saveAndFlush(any(TenantStorageRegistryEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(initializer.initialize(registry.getPhysicalIdentifier(), registry.getExpectedVersion()))
        .thenThrow(new DataAccessResourceFailureException("temporary database connection failure"));
    TenantStorageProvisioningOperationExecutor executor = executor(operations, steps, registries, transitions, audits,
        initializer);

    executor.execute(new StorageOperationClaimVO(operation.getPublicId(), registry.getId(),
        StorageOperationType.PROVISION, "instance-a", NOW.plusSeconds(600)));

    assertThat(operation.getOperationState()).isEqualTo(StorageOperationState.RETRY_WAIT);
    assertThat(operation.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    assertThat(operation.getSafeFailureCode()).isEqualTo("TENANT_STORAGE_TRANSIENT_FAILURE");
    assertThat(operation.getLeaseOwner()).isNull();
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.INITIALIZING);
  }

  @Test
  void execute_shouldQuarantineTenant_whenPhysicalFailureRequiresInfrastructure() {
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    StorageOperationStepRepository steps = mock(StorageOperationStepRepository.class);
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    TenantSchemaInitializer initializer = mock(TenantSchemaInitializer.class);
    StorageOperationEntity operation = operation();
    TenantStorageRegistryEntity registry = registry();
    Map<StorageOperationStepType, StorageOperationStepEntity> stepByType = new EnumMap<>(
        StorageOperationStepType.class);
    stepByType.put(StorageOperationStepType.RESERVE,
        new StorageOperationStepEntity(operation.getId(), StorageOperationStepType.RESERVE));
    configureStepRepository(steps, stepByType);
    when(operations.findByPublicIdForUpdate(operation.getPublicId())).thenReturn(Optional.of(operation));
    when(operations.saveAndFlush(any(StorageOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(registries.findById(registry.getId())).thenReturn(Optional.of(registry));
    when(registries.saveAndFlush(any(TenantStorageRegistryEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(initializer.initialize(registry.getPhysicalIdentifier(), registry.getExpectedVersion()))
        .thenThrow(new RFWDatabaseUpdateException(RFWDatabaseUpdateErrorCategoryEnum.VERSION_CONSISTENCY,
            "incompatible tenant version"));
    TenantStorageProvisioningOperationExecutor executor = executor(operations, steps, registries, transitions, audits,
        initializer);

    executor.execute(new StorageOperationClaimVO(operation.getPublicId(), registry.getId(),
        StorageOperationType.PROVISION, "instance-a", NOW.plusSeconds(600)));

    assertThat(operation.getOperationState()).isEqualTo(StorageOperationState.FAILED_FINAL);
    assertThat(operation.getActiveMarker()).isNull();
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.QUARANTINED);
    assertThat(registry.getQuarantineReasonCode()).isEqualTo("TENANT_STORAGE_REQUIRES_INFRASTRUCTURE");
    assertThat(stepByType.get(StorageOperationStepType.CREATE_SCHEMA).getStepState())
        .isEqualTo(StorageOperationStepState.FAILED);
  }

  private static void configureStepRepository(StorageOperationStepRepository steps,
      Map<StorageOperationStepType, StorageOperationStepEntity> stepByType) {
    when(steps.findByStorageOperationIdAndStepType(any(), any())).thenAnswer(invocation ->
        Optional.ofNullable(stepByType.get(invocation.getArgument(1))));
    when(steps.saveAndFlush(any(StorageOperationStepEntity.class))).thenAnswer(invocation -> {
      StorageOperationStepEntity step = invocation.getArgument(0);
      stepByType.put(step.getStepType(), step);
      return step;
    });
  }

  private static TenantStorageProvisioningOperationExecutor executor(StorageOperationRepository operations,
      StorageOperationStepRepository steps, TenantStorageRegistryRepository registries,
      StorageStateTransitionRepository transitions, StorageAuditEventRepository audits,
      TenantSchemaInitializer initializer) {
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    TransactionStatus transaction = mock(TransactionStatus.class);
    when(transactionManager.getTransaction(any())).thenReturn(transaction);
    return new TenantStorageProvisioningOperationExecutor(operations, steps, registries, transitions, audits,
        new StorageOperationStateTransitionService(), new StorageOperationStepStateTransitionService(),
        new TenantStorageStateTransitionService(), initializer,
        new StoragePropertiesConfig(java.time.Duration.ofSeconds(30), java.time.Duration.ofMinutes(10),
            java.time.Duration.ofSeconds(30), 3, 1), transactionManager,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static StorageOperationEntity operation() {
    StorageOperationEntity operation = new StorageOperationEntity(UUID.randomUUID(), 7L,
        StorageOperationType.PROVISION, UUID.randomUUID(), "provision-test");
    ReflectionTestUtils.setField(operation, "id", 9L);
    operation.claim("instance-a", NOW.plusSeconds(600));
    return operation;
  }

  private static TenantStorageRegistryEntity registry() {
    TenantStorageRegistryEntity registry = new TenantStorageRegistryEntity(12L,
        new TenantPhysicalIdentifier("0123456789abcdef0123456789abcdef"), "20260829001");
    ReflectionTestUtils.setField(registry, "id", 7L);
    return registry;
  }
}
