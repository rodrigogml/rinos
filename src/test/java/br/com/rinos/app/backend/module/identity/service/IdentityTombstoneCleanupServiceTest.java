package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;

@DisplayName("Retenção coordenada dos tombstones")
class IdentityTombstoneCleanupServiceTest {

  @Test
  void cleanup_shouldDeleteFullAndPartialBatchesUsingFifteenDayCutoff() {
    IdentityEventRepository repository = mock(IdentityEventRepository.class);
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.canStartJob()).thenReturn(true);
    when(coordinator.executeBatch(any())).thenAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return true;
    });
    when(repository.deleteTombstoneBatch(
        org.mockito.ArgumentMatchers.eq("REGISTRATION_CANCELLED"),
        any(),
        anyInt())).thenReturn(2, 1);
    when(repository.deleteTombstoneBatch(
        org.mockito.ArgumentMatchers.eq("REGISTRATION_EXPIRED"),
        any(),
        anyInt())).thenReturn(0);
    IdentityTombstoneCleanupService service = new IdentityTombstoneCleanupService(
        repository,
        coordinator,
        new CleanupPropertiesConfig(Duration.ofHours(24), 2));
    Instant executionTime = Instant.parse("2026-07-29T12:00:00Z");

    assertThat(service.cleanup(executionTime)).isEqualTo(3);

    verify(repository, times(2)).deleteTombstoneBatch(
        "REGISTRATION_CANCELLED",
        Instant.parse("2026-07-14T12:00:00Z"),
        2);
  }
}
