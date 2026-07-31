package br.com.rinos.app.backend.module.platform.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Disparador do heartbeat de manutenção")
class MaintenanceHeartbeatSchedulerTest {

  @Test
  void heartbeat_shouldAcquireLease_whenThereIsNoRenewableToken() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.renewLease()).thenReturn(false);
    MaintenanceHeartbeatScheduler scheduler =
        new MaintenanceHeartbeatScheduler(coordinator);

    scheduler.heartbeat();

    verify(coordinator).tryAcquire("global-maintenance");
  }

  @Test
  void heartbeat_shouldNotCompete_whenLeaseWasRenewed() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.renewLease()).thenReturn(true);
    MaintenanceHeartbeatScheduler scheduler =
        new MaintenanceHeartbeatScheduler(coordinator);

    scheduler.heartbeat();

    verify(coordinator, never()).tryAcquire("global-maintenance");
  }

  @Test
  void heartbeat_shouldContainInfrastructureFailure_untilNextTick() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    when(coordinator.renewLease()).thenThrow(new IllegalStateException("database unavailable"));
    MaintenanceHeartbeatScheduler scheduler =
        new MaintenanceHeartbeatScheduler(coordinator);

    scheduler.heartbeat();

    verify(coordinator).renewLease();
  }
}
