package br.com.rinos.app.backend.module.account.component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.account.service.AccountProvisioningOutboxDispatchService;
import br.com.rinos.app.backend.module.account.vo.AccountProvisioningOutboxClaimVO;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

/**
 * Agenda a entrega da outbox de criação exclusivamente na instância de manutenção eleita.
 *
 * <p>Cada item é reclamado e confirmado em transações próprias. A chamada ao storage fica fora
 * dessas transações para evitar segurar lock global durante uma fronteira de módulo; o protocolo
 * persistido torna a repetição segura após queda ou perda de resposta.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Component
public class AccountProvisioningOutboxMaintenanceScheduler {

  private final MaintenanceCoordinatorService coordinator;
  private final AccountProvisioningOutboxDispatchService dispatcher;
  private final String instanceId;
  private final int batchSize;

  /**
   * Cria o agendador que compartilha a eleição global de manutenção.
   *
   * @param coordinator coordenação persistida de liderança
   * @param dispatcher reclamação e entrega da outbox
   * @param maintenanceProperties identificador estável da instância
   * @param accountCreationProperties limite de itens por disparo
   */
  public AccountProvisioningOutboxMaintenanceScheduler(
      MaintenanceCoordinatorService coordinator,
      AccountProvisioningOutboxDispatchService dispatcher,
      MaintenancePropertiesConfig maintenanceProperties,
      AccountCreationPropertiesConfig accountCreationProperties) {
    this.coordinator = coordinator;
    this.dispatcher = dispatcher;
    instanceId = maintenanceProperties.instanceId();
    batchSize = accountCreationProperties.outboxBatchSize();
  }

  /**
   * Processa um lote limitado sem iniciar ou concluir trabalho quando a liderança não estiver válida.
   */
  @Scheduled(fixedDelayString = "${rinos.account-creation.outbox-retry-base:1m}")
  public void dispatch() {
    for (int index = 0; index < batchSize && coordinator.canStartJob(); index++) {
      AtomicReference<AccountProvisioningOutboxClaimVO> claimReference = new AtomicReference<>();
      boolean claimed = coordinator.executeBatch(() -> {
        Optional<AccountProvisioningOutboxClaimVO> claim = dispatcher.claimNext(instanceId);
        claim.ifPresent(claimReference::set);
      });
      AccountProvisioningOutboxClaimVO claim = claimReference.get();
      if (!claimed || claim == null || !coordinator.canStartJob()) {
        return;
      }
      dispatcher.dispatch(claim);
    }
  }
}
