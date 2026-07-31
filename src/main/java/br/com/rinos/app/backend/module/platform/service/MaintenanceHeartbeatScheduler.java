package br.com.rinos.app.backend.module.platform.service;

import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém a candidatura desta instância ao lease global de manutenção.
 *
 * <p>O primeiro disparo tenta adquirir o lease. Disparos posteriores renovam o token vigente ou
 * voltam a disputar quando a sessão não possui liderança comprovada.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
@Lazy(false)
public class MaintenanceHeartbeatScheduler {

  static final String GLOBAL_MAINTENANCE_LEASE_KEY = "global-maintenance";

  private final Supplier<MaintenanceCoordinatorService> coordinatorSupplier;

  /**
   * Cria o disparador sobre a coordenação persistente.
   *
   * @param coordinatorProvider resolução tardia da aquisição, renovação, fencing e observabilidade
   */
  @Autowired
  public MaintenanceHeartbeatScheduler(
      ObjectProvider<MaintenanceCoordinatorService> coordinatorProvider) {
    coordinatorSupplier = coordinatorProvider::getIfAvailable;
  }

  /**
   * Cria o disparador com dependência direta para testes unitários.
   *
   * @param coordinator coordenação controlada
   */
  MaintenanceHeartbeatScheduler(MaintenanceCoordinatorService coordinator) {
    coordinatorSupplier = () -> coordinator;
  }

  /**
   * Renova ou disputa o lease no intervalo fixo definido no arquivo de propriedades.
   *
   * <p>Falhas já invalidam o token e são observadas pelo coordenador. O scheduler as contém para
   * permitir nova tentativa no disparo seguinte sem encerrar a infraestrutura de agendamento.
   */
  @Scheduled(fixedDelayString = "${rinos.maintenance.heartbeat-interval:30m}")
  public void heartbeat() {
    MaintenanceCoordinatorService coordinator = coordinatorSupplier.get();
    if (coordinator == null) {
      return;
    }
    try {
      if (!coordinator.renewLease()) {
        coordinator.tryAcquire(GLOBAL_MAINTENANCE_LEASE_KEY);
      }
    } catch (RuntimeException ignored) {
      // A observabilidade sanitizada e a invalidação do token pertencem ao coordinator.
    }
  }
}
