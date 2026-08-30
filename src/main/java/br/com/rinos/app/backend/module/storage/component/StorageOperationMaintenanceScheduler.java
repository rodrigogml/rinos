package br.com.rinos.app.backend.module.storage.component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.backend.module.storage.service.StorageOperationClaimService;
import br.com.rinos.app.backend.module.storage.service.StorageOperationExecutionPort;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

/**
 * Entrega uma operação estrutural ao executor somente sob liderança global comprovada.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Component
public class StorageOperationMaintenanceScheduler {
  private final MaintenanceCoordinatorService coordinator;
  private final StorageOperationClaimService claims;
  private final List<StorageOperationExecutionPort> executors;
  private final String instanceId;

  /** Cria o agendador que reutiliza a eleição global existente. */
  public StorageOperationMaintenanceScheduler(MaintenanceCoordinatorService coordinator,
      StorageOperationClaimService claims, List<StorageOperationExecutionPort> executors,
      MaintenancePropertiesConfig properties) {
    this.coordinator = coordinator;
    this.claims = claims;
    this.executors = List.copyOf(executors);
    this.instanceId = properties.instanceId();
  }

  /**
   * Processa no máximo uma operação por disparo, somente quando há executor e liderança estável.
   */
  @Scheduled(fixedDelayString = "${rinos.storage.queue-poll-interval:30s}")
  public void dispatch() {
    if (executors.isEmpty() || !coordinator.canStartJob()) {
      return;
    }
    AtomicReference<StorageOperationClaimVO> claimedOperation = new AtomicReference<>();
    coordinator.executeBatch(() -> claims.claimNext(instanceId).ifPresent(claimedOperation::set));
    StorageOperationClaimVO claim = claimedOperation.get();
    if (claim != null && coordinator.canStartJob()) {
      executors.stream()
          .filter(executor -> executor.supports(claim.operationType()))
          .findFirst()
          .ifPresent(executor -> executor.execute(claim));
    }
  }
}
