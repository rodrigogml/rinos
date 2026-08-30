package br.com.rinos.app.backend.module.storage.component;

import java.util.Objects;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.storage.service.TenantMigrationSchedulingService;

/**
 * Inicia a descoberta de migrations de tenant somente depois do startup global bem-sucedido.
 *
 * <p>O inicializador não executa DDL de tenant: ele persiste intenções idempotentes na fila estrutural e marca os
 * tenants pendentes como migrando antes do refresh do contexto terminar. A dependência explícita garante que o
 * inicializador global da RFW seja concluído primeiro; se a migration global falhar, esta etapa não é alcançada.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Component
@DependsOn("databaseUpdateStartupInitializer")
public class TenantMigrationStartupScheduler implements SmartInitializingSingleton {

  private final TenantMigrationSchedulingService schedulingService;

  /**
   * Cria o inicializador do startup depois da dependência explícita do catálogo global da RFW.
   *
   * @param schedulingService serviço que persiste operações de migration idempotentes
   */
  public TenantMigrationStartupScheduler(TenantMigrationSchedulingService schedulingService) {
    this.schedulingService = Objects.requireNonNull(schedulingService, "schedulingService must not be null");
  }

  /**
   * Descobre tenants pendentes antes que o refresh do contexto anuncie disponibilidade operacional.
   */
  @Override
  public void afterSingletonsInstantiated() {
    schedulingService.schedulePendingMigrations();
  }
}
