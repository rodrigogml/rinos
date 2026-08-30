package br.com.rinos.app.backend.module.storage.component;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.storage.service.TenantMigrationSchedulingService;

class TenantMigrationStartupSchedulerTest {

  @Test
  void afterSingletonsInstantiated_shouldScheduleTenantMigrationsAfterGlobalStartup() {
    TenantMigrationSchedulingService schedulingService = mock(TenantMigrationSchedulingService.class);
    TenantMigrationStartupScheduler scheduler = new TenantMigrationStartupScheduler(schedulingService);

    scheduler.afterSingletonsInstantiated();

    verify(schedulingService).schedulePendingMigrations();
  }
}
