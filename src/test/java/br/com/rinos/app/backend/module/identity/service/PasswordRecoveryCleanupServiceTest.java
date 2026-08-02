package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.repository.PasswordRecoveryRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;
import br.com.rinos.app.config.PasswordRecoveryPropertiesConfig;

@DisplayName("Retenção coordenada das provas de recuperação")
class PasswordRecoveryCleanupServiceTest {

  @Test
  void cleanup_shouldDeleteConfiguredBatches_whileLeadershipIsProven() {
    PasswordRecoveryRepository repository = mock(PasswordRecoveryRepository.class);
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.canStartJob()).thenReturn(true);
    when(coordinator.executeBatch(any())).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(0).run();
      return true;
    });
    when(repository.deleteRetentionBatch(any(), eq(2))).thenReturn(2, 1);
    PasswordRecoveryCleanupService service = service(repository, coordinator);
    Instant executionTime = Instant.parse("2026-08-02T12:00:00Z");

    int deleted = service.cleanup(executionTime);

    assertThat(deleted).isEqualTo(3);
    verify(repository, org.mockito.Mockito.times(2)).deleteRetentionBatch(
        executionTime.minus(Duration.ofDays(30)), 2);
  }

  @Test
  void cleanup_shouldRemainSuspended_withoutLeadership() {
    PasswordRecoveryRepository repository = mock(PasswordRecoveryRepository.class);
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);

    int deleted = service(repository, coordinator).cleanup(Instant.now());

    assertThat(deleted).isZero();
    verify(repository, never()).deleteRetentionBatch(any(), eq(2));
  }

  private static PasswordRecoveryCleanupService service(
      PasswordRecoveryRepository repository,
      MaintenanceCoordinatorService coordinator) {
    return new PasswordRecoveryCleanupService(
        repository,
        coordinator,
        new PasswordRecoveryPropertiesConfig(
            Duration.ofHours(1),
            3,
            Duration.ofMinutes(15),
            20,
            Duration.ofMinutes(15),
            Duration.ofDays(30)),
        new CleanupPropertiesConfig(Duration.ofHours(24), 2));
  }
}
