package br.com.rinos.app.backend.module.account.component;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.account.service.AccountCreationSagaAdvanceService;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;

/**
 * Agenda o avanço limitado da saga de conta somente na instância de manutenção eleita.
 *
 * <p>Cada avanço é uma transação global independente, com a prova de liderança repetida antes
 * de começar. O agendador não ativa contas e não abre contexto de tenant; ele somente entrega a
 * próxima etapa persistida ao coordenador canônico.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Component
public class AccountCreationSagaMaintenanceScheduler {

  private final MaintenanceCoordinatorService coordinator;
  private final AccountCreationSagaAdvanceService saga;
  private final int batchSize;

  /**
   * Cria o agendador com o mesmo limite de lote conservador da outbox de cadastro.
   *
   * @param coordinator liderança global persistida
   * @param saga coordenador ordenado dos checkpoints
   * @param properties limites fixos da criação de conta
   */
  public AccountCreationSagaMaintenanceScheduler(
      MaintenanceCoordinatorService coordinator,
      AccountCreationSagaAdvanceService saga,
      AccountCreationPropertiesConfig properties) {
    this.coordinator = coordinator;
    this.saga = saga;
    batchSize = properties.outboxBatchSize();
  }

  /** Avança no máximo um lote de checkpoints elegíveis, sem trabalhar fora da liderança válida. */
  @Scheduled(fixedDelayString = "${rinos.account-creation.outbox-retry-base:1m}")
  public void advance() {
    for (int index = 0; index < batchSize && coordinator.canStartJob(); index++) {
      AdvancementResult result = new AdvancementResult();
      boolean executed = coordinator.executeBatch(() -> result.advanced = saga.advanceNext());
      if (!executed || !result.advanced) {
        return;
      }
    }
  }

  private static final class AdvancementResult {

    private boolean advanced;
  }
}
