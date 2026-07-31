package br.com.rinos.app.backend.module.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.CleanupPropertiesConfig;

/**
 * Remove tombstones de cancelamento depois da retenção mínima documentada.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class IdentityTombstoneCleanupService {

  private static final Duration CANCELLATION_TOMBSTONE_RETENTION = Duration.ofDays(15);

  private final IdentityEventRepository repository;
  private final MaintenanceCoordinatorService coordinator;
  private final CleanupPropertiesConfig cleanupProperties;

  /**
   * Cria a tarefa sobre o registro append-only minimizado.
   *
   * @param repository persistência dos eventos
   * @param coordinator prova de liderança e transações limitadas
   * @param cleanupProperties tamanho dos lotes
   */
  public IdentityTombstoneCleanupService(
      IdentityEventRepository repository,
      MaintenanceCoordinatorService coordinator,
      CleanupPropertiesConfig cleanupProperties) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.coordinator = Objects.requireNonNull(
        coordinator,
        "coordinator must not be null");
    this.cleanupProperties = Objects.requireNonNull(
        cleanupProperties,
        "cleanupProperties must not be null");
  }

  /**
   * Exclui lotes de tombstones vencidos enquanto o lease permanecer comprovado.
   *
   * @param executionTime instante UTC do catálogo
   * @return total removido
   */
  public int cleanup(Instant executionTime) {
    Objects.requireNonNull(executionTime, "executionTime must not be null");
    if (!coordinator.canStartJob()) {
      return 0;
    }
    Instant cutoff = executionTime.minus(CANCELLATION_TOMBSTONE_RETENTION);
    int total = cleanupType(IdentityEventTypeEnum.REGISTRATION_CANCELLED, cutoff);
    return Math.addExact(
        total,
        cleanupType(IdentityEventTypeEnum.REGISTRATION_EXPIRED, cutoff));
  }

  private int cleanupType(IdentityEventTypeEnum eventType, Instant cutoff) {
    int total = 0;
    while (true) {
      AtomicInteger deleted = new AtomicInteger();
      boolean executed = coordinator.executeBatch(() -> deleted.set(
          repository.deleteTombstoneBatch(
              eventType.name(),
              cutoff,
              cleanupProperties.batchSize())));
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
