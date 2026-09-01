package br.com.rinos.app.backend.module.account.component;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.account.service.AccountCreationActivationService;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;

/**
 * Agenda a promoção final das contas prontas exclusivamente na instância de manutenção eleita.
 *
 * <p>O agendador executa uma única tentativa por ciclo para não repetir uma dependência
 * indisponível no mesmo lote. A elegibilidade e a transação pertencem ao serviço de ativação.
 *
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
@Component
public class AccountCreationActivationMaintenanceScheduler {

  private final MaintenanceCoordinatorService coordinator;
  private final AccountCreationActivationService activation;

  /**
   * Cria o agendador de ativação final.
   *
   * @param coordinator liderança global persistida
   * @param activation serviço transacional de promoção final
   */
  public AccountCreationActivationMaintenanceScheduler(
      MaintenanceCoordinatorService coordinator,
      AccountCreationActivationService activation) {
    this.coordinator = coordinator;
    this.activation = activation;
  }

  /** Revalida e promove uma conta pronta quando a liderança de manutenção estiver válida. */
  @Scheduled(fixedDelayString = "${rinos.account-creation.outbox-retry-base:1m}")
  public void activate() {
    if (!coordinator.canStartJob()) {
      return;
    }
    coordinator.executeBatch(() -> activation.activateNext());
  }
}
