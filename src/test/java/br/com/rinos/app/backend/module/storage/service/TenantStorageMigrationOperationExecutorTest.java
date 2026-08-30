package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.component.TenantDataSourceFactory;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseStructureVerifier;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseUpdateRequestFactory;
import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageMigrationExecutionState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageMigrationExecutionRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseUpdateScriptVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateOrchestratorService;
import br.eng.rodrigogml.rfw.database.service.DatabaseVersionService;
import br.eng.rodrigogml.rfw.database.vo.DatabaseUpdateRequestVO;
import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

class TenantStorageMigrationOperationExecutorTest {

  private static final Instant NOW = Instant.parse("2026-08-30T17:00:00Z");
  private static final String TARGET_VERSION = "20260901001";

  @Test
  void execute_shouldConfirmMigrationAndReleaseOnlyTenant_whenRfwUpdateSucceeds() {
    Fixture fixture = new Fixture();

    fixture.executor().execute(fixture.claim());

    assertThat(fixture.operation().getOperationState()).isEqualTo(StorageOperationState.COMPLETED);
    assertThat(fixture.registry().getStorageState()).isEqualTo(TenantStorageState.READY);
    assertThat(fixture.registry().getObservedVersion()).isEqualTo(TARGET_VERSION);
    assertThat(fixture.migrations()).singleElement().satisfies(execution -> {
      assertThat(execution.getExecutionState()).isEqualTo(StorageMigrationExecutionState.COMPLETED);
      assertThat(execution.getPreviousVersion()).isEqualTo("20260829001");
      assertThat(execution.getResultingVersion()).isEqualTo(TARGET_VERSION);
      assertThat(execution.getFinishedAt()).isEqualTo(NOW);
    });
    assertThat(fixture.step().getStepState()).isEqualTo(StorageOperationStepState.COMPLETED);
    verify(fixture.updateOrchestrator()).updateDatabase(any(DatabaseUpdateRequestVO.class));
  }

  @Test
  void execute_shouldQuarantineTenantAndNeverScheduleRetry_whenRfwUpdateFails() {
    Fixture fixture = new Fixture();
    doThrow(new IllegalStateException("controlled migration failure"))
        .when(fixture.updateOrchestrator()).updateDatabase(any(DatabaseUpdateRequestVO.class));

    fixture.executor().execute(fixture.claim());

    assertThat(fixture.operation().getOperationState()).isEqualTo(StorageOperationState.FAILED_FINAL);
    assertThat(fixture.operation().getNextAttemptAt()).isNull();
    assertThat(fixture.registry().getStorageState()).isEqualTo(TenantStorageState.QUARANTINED);
    assertThat(fixture.registry().getQuarantineReasonCode()).isEqualTo("TENANT_MIGRATION_REQUIRES_INFRASTRUCTURE");
    assertThat(fixture.migrations()).singleElement().satisfies(execution ->
        assertThat(execution.getExecutionState()).isEqualTo(StorageMigrationExecutionState.FAILED));
    assertThat(fixture.step().getStepState()).isEqualTo(StorageOperationStepState.FAILED);
  }

  private static final class Fixture {
    private final StorageOperationRepository operations = mock(StorageOperationRepository.class);
    private final StorageOperationStepRepository steps = mock(StorageOperationStepRepository.class);
    private final TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    private final StorageMigrationExecutionRepository migrationRepository = mock(StorageMigrationExecutionRepository.class);
    private final StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    private final StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    private final TenantDataSourceFactory dataSourceFactory = mock(TenantDataSourceFactory.class);
    private final TenantDatabaseUpdateRequestFactory requestFactory = mock(TenantDatabaseUpdateRequestFactory.class);
    private final TenantDatabaseCatalogService catalogService = mock(TenantDatabaseCatalogService.class);
    private final TenantDatabaseStructureVerifier structureVerifier = mock(TenantDatabaseStructureVerifier.class);
    private final DatabaseVersionService versionService = mock(DatabaseVersionService.class);
    private final DatabaseUpdateOrchestratorService updateOrchestrator = mock(DatabaseUpdateOrchestratorService.class);
    private final HikariDataSource tenantDataSource = mock(HikariDataSource.class);
    private final StorageOperationEntity operation = TenantStorageMigrationOperationExecutorTest.operation();
    private final TenantStorageRegistryEntity registry = TenantStorageMigrationOperationExecutorTest.registry();
    private final StorageOperationStepEntity step = new StorageOperationStepEntity(operation.getId(),
        StorageOperationStepType.MIGRATE);
    private final List<StorageMigrationExecutionEntity> migrations = new ArrayList<>();

    private Fixture() {
      when(operations.findByPublicIdForUpdate(operation.getPublicId())).thenReturn(Optional.of(operation));
      when(operations.saveAndFlush(any(StorageOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
      when(registries.findById(registry.getId())).thenReturn(Optional.of(registry));
      when(registries.saveAndFlush(any(TenantStorageRegistryEntity.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(steps.findByStorageOperationIdAndStepType(operation.getId(), StorageOperationStepType.MIGRATE))
          .thenReturn(Optional.of(step));
      when(steps.saveAndFlush(any(StorageOperationStepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
      when(dataSourceFactory.create(registry.getPhysicalIdentifier())).thenReturn(tenantDataSource);
      when(requestFactory.create(tenantDataSource)).thenReturn(mock(DatabaseUpdateRequestVO.class));
      when(versionService.readCurrentVersion(tenantDataSource)).thenReturn(new DatabaseVersionVO("20260829001"));
      when(catalogService.inspect()).thenReturn(catalog());
      when(migrationRepository.findByTenantStorageRegistryIdAndScriptVersion(registry.getId(), TARGET_VERSION))
          .thenAnswer(invocation -> migrations.stream().filter(execution -> TARGET_VERSION.equals(
              execution.getScriptVersion())).findFirst());
      when(migrationRepository.saveAndFlush(any(StorageMigrationExecutionEntity.class))).thenAnswer(invocation -> {
        StorageMigrationExecutionEntity execution = invocation.getArgument(0);
        if (!migrations.contains(execution)) {
          migrations.add(execution);
        }
        return execution;
      });
      when(migrationRepository.findAllByTenantStorageRegistryIdOrderByScriptVersion(registry.getId()))
          .thenAnswer(invocation -> List.copyOf(migrations));
      when(migrationRepository.findAllByStorageOperationIdOrderByScriptVersion(operation.getId()))
          .thenAnswer(invocation -> List.copyOf(migrations));
    }

    private TenantStorageMigrationOperationExecutor executor() {
      PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
      TransactionStatus transaction = mock(TransactionStatus.class);
      when(transactionManager.getTransaction(any())).thenReturn(transaction);
      return new TenantStorageMigrationOperationExecutor(operations, steps, registries, migrationRepository,
          transitions, audits, new StorageOperationStateTransitionService(),
          new StorageOperationStepStateTransitionService(), new TenantStorageStateTransitionService(),
          dataSourceFactory, requestFactory, catalogService, structureVerifier, versionService, updateOrchestrator,
          transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private StorageOperationClaimVO claim() {
      return new StorageOperationClaimVO(operation.getPublicId(), registry.getId(), StorageOperationType.MIGRATE,
          "instance-a", NOW.plusSeconds(600));
    }

    private StorageOperationEntity operation() { return operation; }
    private TenantStorageRegistryEntity registry() { return registry; }
    private StorageOperationStepEntity step() { return step; }
    private List<StorageMigrationExecutionEntity> migrations() { return migrations; }
    private DatabaseUpdateOrchestratorService updateOrchestrator() { return updateOrchestrator; }
  }

  private static StorageOperationEntity operation() {
    StorageOperationEntity operation = new StorageOperationEntity(UUID.randomUUID(), 7L,
        StorageOperationType.MIGRATE, UUID.randomUUID(), "migration-test");
    ReflectionTestUtils.setField(operation, "id", 9L);
    operation.claim("instance-a", NOW.plusSeconds(600));
    return operation;
  }

  private static TenantStorageRegistryEntity registry() {
    TenantStorageRegistryEntity registry = new TenantStorageRegistryEntity(12L,
        new TenantPhysicalIdentifier("0123456789abcdef0123456789abcdef"), TARGET_VERSION);
    ReflectionTestUtils.setField(registry, "id", 7L);
    registry.changeState(TenantStorageState.MIGRATING);
    return registry;
  }

  private static TenantDatabaseCatalogVO catalog() {
    TenantDatabaseUpdateScriptVO script = new TenantDatabaseUpdateScriptVO("20260901_001_update.sql",
        new DatabaseVersionVO(TARGET_VERSION), new byte[32]);
    return new TenantDatabaseCatalogVO(List.of(script), new DatabaseVersionVO(TARGET_VERSION));
  }
}
