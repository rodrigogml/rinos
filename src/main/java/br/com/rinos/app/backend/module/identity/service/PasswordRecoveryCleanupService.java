package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.repository.PasswordRecoveryRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;
import br.com.rinos.app.config.PasswordRecoveryPropertiesConfig;

/**
 * Remove provas de recuperação encerradas depois da retenção técnica configurada.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@Service
@Lazy
public class PasswordRecoveryCleanupService {

  private final PasswordRecoveryRepository repository;
  private final MaintenanceCoordinatorService coordinator;
  private final PasswordRecoveryPropertiesConfig recoveryProperties;
  private final CleanupPropertiesConfig cleanupProperties;

  /**
   * Cria a tarefa protegida pelo lease global.
   *
   * @param repository persistência das provas
   * @param coordinator coordenação do lease global
   * @param recoveryProperties retenção das provas
   * @param cleanupProperties tamanho dos lotes
   */
  public PasswordRecoveryCleanupService(
      PasswordRecoveryRepository repository,
      MaintenanceCoordinatorService coordinator,
      PasswordRecoveryPropertiesConfig recoveryProperties,
      CleanupPropertiesConfig cleanupProperties) {
    this.repository = repository;
    this.coordinator = coordinator;
    this.recoveryProperties = recoveryProperties;
    this.cleanupProperties = cleanupProperties;
  }

  /**
   * Exclui lotes vencidos enquanto a liderança permanecer comprovada.
   *
   * @param executionTime instante UTC do catálogo
   * @return total removido
   */
  public int cleanup(Instant executionTime) {
    Objects.requireNonNull(executionTime, "executionTime must not be null");
    if (!coordinator.canStartJob()) {
      return 0;
    }
    Instant cutoff = executionTime.minus(recoveryProperties.retention());
    int total = 0;
    while (true) {
      AtomicInteger deleted = new AtomicInteger();
      boolean executed = coordinator.executeBatch(() -> deleted.set(
          repository.deleteRetentionBatch(cutoff, cleanupProperties.batchSize())));
      if (!executed) {
        return total;
      }
      total = Math.addExact(total, deleted.get());
      if (deleted.get() < cleanupProperties.batchSize()) {
        return total;
      }
    }
  }
}
