package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.storage.component.TenantPhysicalSchemaInventoryService;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageDivergenceType;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.vo.TenantStorageReconciliationSnapshotVO;

class TenantStorageReconciliationInspectionServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-30T20:00:00Z");

  @Test
  void inspect_shouldReportOnlySafeDivergencesWithoutMutatingRegistryOrOperation() {
    TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    StorageOperationRepository operations = mock(StorageOperationRepository.class);
    TenantPhysicalSchemaInventoryService inventory = mock(TenantPhysicalSchemaInventoryService.class);
    TenantStorageRegistryEntity missingRegistry = registry(7L, "11111111111111111111111111111111");
    TenantStorageRegistryEntity presentRegistry = registry(8L, "22222222222222222222222222222222");
    StorageOperationEntity stalledOperation = new StorageOperationEntity(java.util.UUID.randomUUID(), 8L,
        StorageOperationType.PROVISION, java.util.UUID.randomUUID(), "inspection-test");
    when(registries.findAll()).thenReturn(List.of(missingRegistry, presentRegistry));
    when(inventory.findTenantSchemas()).thenReturn(Set.of(presentRegistry.getPhysicalIdentifier(),
        new TenantPhysicalIdentifier("33333333333333333333333333333333")));
    when(operations.findAllStalledForReconciliation(NOW)).thenReturn(List.of(stalledOperation));
    TenantStorageReconciliationInspectionService service = new TenantStorageReconciliationInspectionService(
        registries, operations, inventory, Clock.fixed(NOW, ZoneOffset.UTC));

    TenantStorageReconciliationSnapshotVO result = service.inspect();

    assertThat(result.unregisteredSchemaCount()).isEqualTo(1);
    assertThat(result.divergences()).extracting(divergence -> divergence.tenantStorageRegistryId())
        .containsExactlyInAnyOrder(7L, 8L);
    assertThat(result.divergences()).extracting(divergence -> divergence.type())
        .containsExactlyInAnyOrder(TenantStorageDivergenceType.REGISTRY_SCHEMA_MISSING,
            TenantStorageDivergenceType.OPERATION_WITHOUT_PROGRESS);
    verify(registries, never()).save(any());
    verify(operations, never()).save(any());
  }

  private static TenantStorageRegistryEntity registry(Long id, String identifier) {
    TenantStorageRegistryEntity registry = new TenantStorageRegistryEntity(id,
        new TenantPhysicalIdentifier(identifier), "20260829001");
    ReflectionTestUtils.setField(registry, "id", id);
    return registry;
  }
}
