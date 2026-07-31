package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.repository.OriginWindowRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;
import br.com.rinos.app.config.OriginPropertiesConfig;

/**
 * Tarefa do catálogo diário que remove janelas vencidas em lotes coordenados próprios.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class OriginWindowCleanupService {

  private final OriginWindowRepository repository;
  private final MaintenanceCoordinatorService coordinator;
  private final OriginPropertiesConfig originProperties;
  private final CleanupPropertiesConfig cleanupProperties;

  /**
   * Cria a tarefa submetida à liderança global já existente.
   *
   * @param repository persistência das janelas
   * @param coordinator fencing e transações limitadas
   * @param originProperties retenção após a janela
   * @param cleanupProperties tamanho dos lotes
   */
  public OriginWindowCleanupService(
      OriginWindowRepository repository,
      MaintenanceCoordinatorService coordinator,
      OriginPropertiesConfig originProperties,
      CleanupPropertiesConfig cleanupProperties) {
    this.repository = repository;
    this.coordinator = coordinator;
    this.originProperties = originProperties;
    this.cleanupProperties = cleanupProperties;
  }

  /**
   * Executa todos os lotes vencidos enquanto a sessão comprovar liderança.
   *
   * @param executionTime instante UTC do catálogo
   * @return total removido; zero quando a tarefa permaneceu suspensa
   */
  public int cleanup(Instant executionTime) {
    Objects.requireNonNull(executionTime, "executionTime must not be null");
    if (!coordinator.canStartJob()) {
      return 0;
    }
    Instant cutoff = executionTime.minus(originProperties.retentionAfterWindow());
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
