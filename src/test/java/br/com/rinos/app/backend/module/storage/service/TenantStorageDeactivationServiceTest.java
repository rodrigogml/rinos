package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.module.storage.enums.TenantStorageDeactivationStatusEnum;
import br.com.rinos.app.api.module.storage.vo.TenantStorageDeactivationResultVO;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

class TenantStorageDeactivationServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-30T22:00:00Z");
  private static final UUID TENANT_PUBLIC_ID = UUID.fromString("f04d5d17-2a4c-47c3-89f1-fc50d0eacd55");

  @Test
  void requestDeactivation_shouldBlockNewUseAndPreserveStorageWithoutDeletion() {
    Fixture fixture = new Fixture();
    TenantEntity tenant = tenant(44L);
    TenantStorageRegistryEntity registry = registry(7L, 44L, TenantStorageState.READY);
    when(fixture.tenants.findByPublicId(TENANT_PUBLIC_ID)).thenReturn(Optional.of(tenant));
    when(fixture.registries.findByTenantIdForUpdate(44L)).thenReturn(Optional.of(registry));

    TenantStorageDeactivationResultVO result = fixture.service.requestDeactivation(
        TENANT_PUBLIC_ID, 99L, "deactivation-test", NOW);

    assertThat(result.status()).isEqualTo(TenantStorageDeactivationStatusEnum.DEACTIVATION_REQUESTED);
    assertThat(registry.getStorageState()).isEqualTo(TenantStorageState.DEACTIVATING);
    verify(fixture.transitions).transition(TenantStorageState.READY, TenantStorageState.DEACTIVATING,
        StorageTransitionOriginType.GLOBAL_USER);
    verify(fixture.registries).save(registry);
    ArgumentCaptor<StorageStateTransitionEntity> transition = ArgumentCaptor.forClass(
        StorageStateTransitionEntity.class);
    verify(fixture.transitionEvents).save(transition.capture());
    assertThat(transition.getValue().getPreviousState()).isEqualTo(TenantStorageState.READY);
    assertThat(transition.getValue().getResultingState()).isEqualTo(TenantStorageState.DEACTIVATING);
    assertThat(transition.getValue().getActorUserId()).isEqualTo(99L);
    ArgumentCaptor<StorageAuditEventEntity> audit = ArgumentCaptor.forClass(StorageAuditEventEntity.class);
    verify(fixture.audits).save(audit.capture());
    assertThat(audit.getValue().getSafeResultCode())
        .isEqualTo("TENANT_STORAGE_DEACTIVATION_PENDING_GOVERNANCE");
    verify(fixture.registries, never()).delete(any());
  }

  @Test
  void requestDeactivation_shouldRemainIdempotentWhenGovernanceIsAlreadyPending() {
    Fixture fixture = new Fixture();
    when(fixture.tenants.findByPublicId(TENANT_PUBLIC_ID)).thenReturn(Optional.of(tenant(44L)));
    when(fixture.registries.findByTenantIdForUpdate(44L)).thenReturn(Optional.of(
        registry(7L, 44L, TenantStorageState.DEACTIVATING)));

    TenantStorageDeactivationResultVO result = fixture.service.requestDeactivation(
        TENANT_PUBLIC_ID, 99L, "deactivation-test", NOW);

    assertThat(result.status()).isEqualTo(TenantStorageDeactivationStatusEnum.ALREADY_DEACTIVATING);
    verify(fixture.transitions, never()).transition(any(), any(), any());
    verify(fixture.registries, never()).save(any());
    verify(fixture.transitionEvents, never()).save(any());
    verify(fixture.audits).save(any(StorageAuditEventEntity.class));
  }

  private static TenantEntity tenant(long id) {
    TenantEntity tenant = new TenantEntity(TENANT_PUBLIC_ID);
    ReflectionTestUtils.setField(tenant, "id", id);
    return tenant;
  }

  private static TenantStorageRegistryEntity registry(long id, long tenantId, TenantStorageState state) {
    TenantStorageRegistryEntity registry = new TenantStorageRegistryEntity(tenantId,
        new TenantPhysicalIdentifier("11111111111111111111111111111111"), "20260830001");
    ReflectionTestUtils.setField(registry, "id", id);
    ReflectionTestUtils.setField(registry, "storageState", state);
    return registry;
  }

  private static final class Fixture {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    private final StorageStateTransitionRepository transitionEvents = mock(StorageStateTransitionRepository.class);
    private final StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    private final TenantStorageStateTransitionService transitions =
        mock(TenantStorageStateTransitionService.class);
    private final TenantStorageDeactivationService service = new TenantStorageDeactivationService(
        tenants, registries, transitionEvents, audits, transitions);
  }
}
