package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;
import br.com.rinos.app.api.module.storage.enums.TenantStorageCreatorStatusEnum;
import br.com.rinos.app.api.module.storage.vo.TenantStorageCreatorSummaryVO;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;

@DisplayName("Resumo público do armazenamento de tenant")
class TenantStorageCreatorSummaryServiceTest {

  private static final Instant OBSERVED_AT = Instant.parse("2026-08-30T12:00:00Z");

  private final TenantStorageCreatorSummaryService service = new TenantStorageCreatorSummaryService();

  @Test
  void summarize_shouldMapInternalAvailabilityToOnlyCreatorStates_whenSnapshotIsNotReady() {
    assertThat(summaryOf(true, true, false, TenantStorageAvailabilityEnum.WAITING).status())
        .isEqualTo(TenantStorageCreatorStatusEnum.WAITING);
    assertThat(summaryOf(true, true, false, TenantStorageAvailabilityEnum.MIGRATING).status())
        .isEqualTo(TenantStorageCreatorStatusEnum.PREPARING);
    assertThat(summaryOf(true, true, false, TenantStorageAvailabilityEnum.ATTENTION).status())
        .isEqualTo(TenantStorageCreatorStatusEnum.ATTENTION);
    assertThat(summaryOf(true, true, false, TenantStorageAvailabilityEnum.INACTIVE).status())
        .isEqualTo(TenantStorageCreatorStatusEnum.ATTENTION);
  }

  @Test
  void summarize_shouldReturnReady_whenSnapshotIsReady() {
    TenantStorageCreatorSummaryVO summary = summaryOf(true, true, true,
        TenantStorageAvailabilityEnum.READY);

    assertThat(summary.status()).isEqualTo(TenantStorageCreatorStatusEnum.READY);
    assertThat(summary.observedAt()).isEqualTo(OBSERVED_AT);
  }

  @Test
  void creatorSummary_shouldExposeNoPhysicalInfrastructureField_whenContractIsInspected() {
    String[] fieldNames = Arrays.stream(TenantStorageCreatorSummaryVO.class.getRecordComponents())
        .map(RecordComponent::getName)
        .toArray(String[]::new);

    assertThat(fieldNames).containsExactly("status", "observedAt");
    assertThat(fieldNames).noneMatch(name -> name.toLowerCase().contains("physical")
        || name.toLowerCase().contains("schema") || name.toLowerCase().contains("url")
        || name.toLowerCase().contains("host") || name.toLowerCase().contains("version"));
  }

  private TenantStorageCreatorSummaryVO summaryOf(boolean sourceAvailable, boolean tenantKnown,
      boolean ready, TenantStorageAvailabilityEnum availability) {
    return service.summarize(new TenantStorageReadinessSnapshotVO(sourceAvailable, tenantKnown, ready,
        availability, ready ? null : "TENANT_STORAGE_NOT_READY", OBSERVED_AT));
  }
}
