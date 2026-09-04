package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseUpdateScriptVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

class TenantMigrationSchedulingServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-30T18:00:00Z");
  private static final String TARGET_VERSION = "20260901001";

  @Test
  void schedulePendingMigrations_shouldQueueAndBlockReadyTenant_whenCatalogRequiresNewVersion() {
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    TenantDatabaseCatalogService catalogService = mock(TenantDatabaseCatalogService.class);
    TenantStorageRegistryEntity registry = registry("20260829001");
    when(registries.findAllReadyForMigrationScheduling()).thenReturn(List.of(registry));
    when(catalogService.inspect()).thenReturn(catalog());
    when(operations.saveAndFlush(any(StorageOperationEntity.class))).thenAnswer(invocation -> {
      StorageOperationEntity operation = invocation.getArgument(0);
      ReflectionTestUtils.setField(operation, "id", 19L);
      return operation;
    });
    when(registries.saveAndFlush(any(TenantStorageRegistryEntity.class))).thenAnswer(invocation ->
        invocation.getArgument(0));

    int scheduled = service(registries, operations, transitions, audits, catalogService).schedulePendingMigrations();

    assertThat(scheduled).isEqualTo(1);
    assertThat(registry.getExpectedVersion()).isEqualTo(TARGET_VERSION);
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.MIGRATING);
    verify(operations).saveAndFlush(any(StorageOperationEntity.class));
    verify(transitions).save(any());
    verify(audits).save(any());
  }

  @Test
  void schedulePendingMigrations_shouldLeaveCurrentTenantReady_whenExpectedVersionAlreadyMatchesCatalog() {
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    TenantDatabaseCatalogService catalogService = mock(TenantDatabaseCatalogService.class);
    TenantStorageRegistryEntity registry = registry(TARGET_VERSION);
    when(registries.findAllReadyForMigrationScheduling()).thenReturn(List.of(registry));
    when(catalogService.inspect()).thenReturn(catalog());

    int scheduled = service(registries, operations, transitions, audits, catalogService).schedulePendingMigrations();

    assertThat(scheduled).isZero();
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.READY);
  }

  private static TenantMigrationSchedulingService service(TenantStorageRegistryRepository registries,
      StorageOperationRepository operations, StorageStateTransitionRepository transitions,
      StorageAuditEventRepository audits, TenantDatabaseCatalogService catalogService) {
    return new TenantMigrationSchedulingService(registries, operations, transitions, audits, catalogService,
        new TenantStorageStateTransitionService(), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static TenantStorageRegistryEntity registry(String expectedVersion) {
    TenantStorageRegistryEntity registry = new TenantStorageRegistryEntity(12L,
        new TenantPhysicalIdentifier("0123456789abcdef0123456789abcdef"), expectedVersion);
    ReflectionTestUtils.setField(registry, "id", 7L);
    registry.changeState(TenantStorageState.READY);
    return registry;
  }

  private static TenantDatabaseCatalogVO catalog() {
    TenantDatabaseUpdateScriptVO script = new TenantDatabaseUpdateScriptVO("20260901_001_update.sql",
        new DatabaseVersionVO(TARGET_VERSION), new byte[32]);
    return new TenantDatabaseCatalogVO(List.of(script), new DatabaseVersionVO(TARGET_VERSION));
  }
}
