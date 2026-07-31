package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.repository.OriginWindowRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;
import br.com.rinos.app.config.OriginPropertiesConfig;

@DisplayName("Limpeza coordenada das janelas de origem")
class OriginWindowCleanupServiceTest {

  @Test
  void cleanup_shouldRemainSuspended_whenLeaseIsNotStable() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.canStartJob()).thenReturn(false);
    OriginWindowCleanupService service = service(repository, coordinator);

    assertThat(service.cleanup(Instant.parse("2026-07-29T18:00:00Z"))).isZero();
    verify(repository, never()).deleteRetentionBatch(any(), anyInt());
  }

  @Test
  void cleanup_shouldExecuteIndependentBatchesUntilLastPartialBatch() {
    OriginWindowRepository repository = mock(OriginWindowRepository.class);
    when(repository.deleteRetentionBatch(
        Instant.parse("2026-06-29T18:00:00Z"),
        2))
        .thenReturn(2, 1);
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.canStartJob()).thenReturn(true);
    doAnswer(invocation -> {
      invocation.getArgument(0, Runnable.class).run();
      return true;
    }).when(coordinator).executeBatch(any(Runnable.class));
    OriginWindowCleanupService service = service(repository, coordinator);

    assertThat(service.cleanup(Instant.parse("2026-07-29T18:00:00Z"))).isEqualTo(3);
    verify(coordinator, org.mockito.Mockito.times(2)).executeBatch(any(Runnable.class));
  }

  private static OriginWindowCleanupService service(
      OriginWindowRepository repository,
      MaintenanceCoordinatorService coordinator) {
    return new OriginWindowCleanupService(
        repository,
        coordinator,
        new OriginPropertiesConfig(0, 20, Duration.ofHours(24), Duration.ofDays(30)),
        new CleanupPropertiesConfig(Duration.ofDays(1), 2));
  }
}
