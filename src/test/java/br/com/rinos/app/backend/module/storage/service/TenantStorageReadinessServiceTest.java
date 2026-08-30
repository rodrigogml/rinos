package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;

@DisplayName("Gate de prontidão do armazenamento de tenant")
class TenantStorageReadinessServiceTest {

  private static final Instant OBSERVED_AT = Instant.parse("2026-08-30T12:00:00Z");

  private TenantRepository tenantRepository;
  private TenantStorageRegistryRepository registryRepository;
  private TenantStorageReadinessService service;
  private UUID tenantPublicId;
  private TenantEntity tenant;

  @BeforeEach
  void setUp() {
    tenantRepository = mock(TenantRepository.class);
    registryRepository = mock(TenantStorageRegistryRepository.class);
    service = new TenantStorageReadinessService(tenantRepository, registryRepository,
        Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));
    tenantPublicId = UUID.randomUUID();
    tenant = mock(TenantEntity.class);
    when(tenant.getId()).thenReturn(42L);
  }

  @Nested
  @DisplayName("quando o catálogo global está disponível")
  class CatalogAvailable {

    @Test
    void inspect_shouldReturnReady_whenStateAndObservedVersionAreExact() {
      TenantStorageRegistryEntity registry = registry(TenantStorageState.READY, "20260829001",
          "20260829001");
      when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
      when(registryRepository.findByTenantId(42L)).thenReturn(Optional.of(registry));

      TenantStorageReadinessSnapshotVO snapshot = service.inspect(tenantPublicId);

      assertThat(snapshot.sourceAvailable()).isTrue();
      assertThat(snapshot.tenantKnown()).isTrue();
      assertThat(snapshot.ready()).isTrue();
      assertThat(snapshot.availability()).isEqualTo(TenantStorageAvailabilityEnum.READY);
      assertThat(snapshot.safeReasonCode()).isNull();
      assertThat(snapshot.observedAt()).isEqualTo(OBSERVED_AT);
    }

    @Test
    void inspect_shouldReturnAttention_whenReadyStateHasIncompatibleVersion() {
      TenantStorageRegistryEntity registry = registry(TenantStorageState.READY, "20260829001",
          "20260828001");
      when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
      when(registryRepository.findByTenantId(42L)).thenReturn(Optional.of(registry));

      TenantStorageReadinessSnapshotVO snapshot = service.inspect(tenantPublicId);

      assertThat(snapshot.ready()).isFalse();
      assertThat(snapshot.availability()).isEqualTo(TenantStorageAvailabilityEnum.ATTENTION);
      assertThat(snapshot.safeReasonCode()).isEqualTo("TENANT_STORAGE_INCOMPATIBLE");
    }

    @Test
    void inspect_shouldReturnAttention_whenStorageIsQuarantined() {
      TenantStorageRegistryEntity registry = registry(TenantStorageState.QUARANTINED, "20260829001",
          "20260829001");
      when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
      when(registryRepository.findByTenantId(42L)).thenReturn(Optional.of(registry));

      TenantStorageReadinessSnapshotVO snapshot = service.inspect(tenantPublicId);

      assertThat(snapshot.ready()).isFalse();
      assertThat(snapshot.availability()).isEqualTo(TenantStorageAvailabilityEnum.ATTENTION);
      assertThat(snapshot.safeReasonCode()).isEqualTo("TENANT_STORAGE_NOT_READY");
    }

    @Test
    void inspect_shouldReturnWaiting_whenTenantHasNoStorageRegistry() {
      when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.of(tenant));
      when(registryRepository.findByTenantId(42L)).thenReturn(Optional.empty());

      TenantStorageReadinessSnapshotVO snapshot = service.inspect(tenantPublicId);

      assertThat(snapshot.sourceAvailable()).isTrue();
      assertThat(snapshot.tenantKnown()).isTrue();
      assertThat(snapshot.ready()).isFalse();
      assertThat(snapshot.availability()).isEqualTo(TenantStorageAvailabilityEnum.WAITING);
    }

    @Test
    void inspect_shouldNotIdentifyUnknownTenantAsReady_whenTenantDoesNotExist() {
      when(tenantRepository.findByPublicId(tenantPublicId)).thenReturn(Optional.empty());

      TenantStorageReadinessSnapshotVO snapshot = service.inspect(tenantPublicId);

      assertThat(snapshot.sourceAvailable()).isTrue();
      assertThat(snapshot.tenantKnown()).isFalse();
      assertThat(snapshot.ready()).isFalse();
      assertThat(snapshot.availability()).isEqualTo(TenantStorageAvailabilityEnum.ATTENTION);
    }
  }

  @Test
  void inspect_shouldFailClosed_whenGlobalCatalogReadFails() {
    when(tenantRepository.findByPublicId(tenantPublicId)).thenThrow(new IllegalStateException("database unavailable"));

    TenantStorageReadinessSnapshotVO snapshot = service.inspect(tenantPublicId);

    assertThat(snapshot.sourceAvailable()).isFalse();
    assertThat(snapshot.tenantKnown()).isFalse();
    assertThat(snapshot.ready()).isFalse();
    assertThat(snapshot.availability()).isEqualTo(TenantStorageAvailabilityEnum.ATTENTION);
    assertThat(snapshot.safeReasonCode()).isEqualTo("TENANT_STORAGE_UNAVAILABLE");
  }

  private static TenantStorageRegistryEntity registry(TenantStorageState state, String expectedVersion,
      String observedVersion) {
    TenantStorageRegistryEntity registry = mock(TenantStorageRegistryEntity.class);
    when(registry.getStorageState()).thenReturn(state);
    when(registry.getExpectedVersion()).thenReturn(expectedVersion);
    when(registry.getObservedVersion()).thenReturn(observedVersion);
    return registry;
  }
}
