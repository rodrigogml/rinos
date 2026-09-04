package br.com.rinos.app.backend.module.storage.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.service.StorageOperationClaimService;
import br.com.rinos.app.backend.module.storage.service.StorageOperationExecutionPort;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

class StorageOperationMaintenanceSchedulerTest {

  @Test
  void dispatch_shouldNotClaimOperation_whenExecutorIsNotAvailable() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    StorageOperationClaimService claims = mock(StorageOperationClaimService.class);
    StorageOperationMaintenanceScheduler scheduler = scheduler(coordinator, claims, List.of());

    scheduler.dispatch();

    verifyNoInteractions(coordinator, claims);
  }

  @Test
  void dispatch_shouldNotClaimOperation_whenInstanceIsNotMaintenanceLeader() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    StorageOperationClaimService claims = mock(StorageOperationClaimService.class);
    StorageOperationExecutionPort executor = mock(StorageOperationExecutionPort.class);
    when(coordinator.canStartJob()).thenReturn(false);
    StorageOperationMaintenanceScheduler scheduler = scheduler(coordinator, claims, List.of(executor));

    scheduler.dispatch();

    verify(coordinator).canStartJob();
    verify(coordinator, never()).executeBatch(any(Runnable.class));
    verifyNoInteractions(claims, executor);
  }

  @Test
  void dispatch_shouldExecuteClaimedOperation_whenLeaderAndExecutorAreAvailable() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    StorageOperationClaimService claims = mock(StorageOperationClaimService.class);
    StorageOperationExecutionPort executor = mock(StorageOperationExecutionPort.class);
    StorageOperationClaimVO claim = new StorageOperationClaimVO(UUID.randomUUID(), 8L,
        StorageOperationType.PROVISION, "instance-a", Instant.parse("2026-08-30T13:00:00Z"));
    when(coordinator.canStartJob()).thenReturn(true, true);
    when(executor.supports(StorageOperationType.PROVISION)).thenReturn(true);
    when(coordinator.executeBatch(any(Runnable.class))).thenAnswer(invocation -> {
      Runnable batch = invocation.getArgument(0);
      batch.run();
      return true;
    });
    when(claims.claimNext("instance-a")).thenReturn(Optional.of(claim));
    StorageOperationMaintenanceScheduler scheduler = scheduler(coordinator, claims, List.of(executor));

    scheduler.dispatch();

    verify(claims).claimNext("instance-a");
    verify(executor).execute(claim);
  }

  @Test
  void dispatch_shouldNotExecutePhysicalOperation_whenLeadershipIsLostAfterClaim() {
    MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
    StorageOperationClaimService claims = mock(StorageOperationClaimService.class);
    StorageOperationExecutionPort executor = mock(StorageOperationExecutionPort.class);
    StorageOperationClaimVO claim = new StorageOperationClaimVO(UUID.randomUUID(), 8L,
        StorageOperationType.PROVISION, "instance-a", Instant.parse("2026-08-30T13:00:00Z"));
    when(coordinator.canStartJob()).thenReturn(true, false);
    when(coordinator.executeBatch(any(Runnable.class))).thenAnswer(invocation -> {
      Runnable batch = invocation.getArgument(0);
      batch.run();
      return true;
    });
    when(claims.claimNext("instance-a")).thenReturn(Optional.of(claim));
    StorageOperationMaintenanceScheduler scheduler = scheduler(coordinator, claims, List.of(executor));

    scheduler.dispatch();

    verify(claims).claimNext("instance-a");
    verify(executor, never()).execute(claim);
  }

  private static StorageOperationMaintenanceScheduler scheduler(MaintenanceCoordinatorService coordinator,
      StorageOperationClaimService claims, List<StorageOperationExecutionPort> executors) {
    MaintenancePropertiesConfig properties = new MaintenancePropertiesConfig("instance-a", Duration.ofMinutes(30),
        Duration.ofHours(4), Duration.ofMinutes(10), Duration.ofMinutes(5));
    return new StorageOperationMaintenanceScheduler(coordinator, claims, executors, properties);
  }
}
