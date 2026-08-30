package br.com.rinos.app.backend.module.storage.component;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.ObjectProvider;
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
  private final ObjectProvider<StorageOperationExecutionPort> executorProvider;
  private final String instanceId;

  /** Cria o agendador que reutiliza a eleição global existente. */
  public StorageOperationMaintenanceScheduler(MaintenanceCoordinatorService coordinator,
      StorageOperationClaimService claims, ObjectProvider<StorageOperationExecutionPort> executorProvider,
      MaintenancePropertiesConfig properties) {
    this.coordinator = coordinator;
    this.claims = claims;
    this.executorProvider = executorProvider;
    this.instanceId = properties.instanceId();
  }

  /**
   * Processa no máximo uma operação por disparo, somente quando há executor e liderança estável.
   */
  @Scheduled(fixedDelayString = "${rinos.storage.queue-poll-interval:30s}")
  public void dispatch() {
    StorageOperationExecutionPort executor = executorProvider.getIfAvailable();
    if (executor == null || !coordinator.canStartJob()) {
      return;
    }
    AtomicReference<StorageOperationClaimVO> claimedOperation = new AtomicReference<>();
    coordinator.executeBatch(() -> claims.claimNext(instanceId).ifPresent(claimedOperation::set));
    StorageOperationClaimVO claim = claimedOperation.get();
    if (claim != null && coordinator.canStartJob()) {
      executor.execute(claim);
    }
  }
}
