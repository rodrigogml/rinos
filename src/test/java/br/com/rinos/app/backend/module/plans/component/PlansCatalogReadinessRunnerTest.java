package br.com.rinos.app.backend.module.plans.component;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.plans.service.PlansCatalogReadinessService;

class PlansCatalogReadinessRunnerTest {

  @Test
  void run_shouldValidateCatalogBeforeReadiness() {
    PlansCatalogReadinessService readiness = mock(PlansCatalogReadinessService.class);

    new PlansCatalogReadinessRunner(readiness).run(null);

    verify(readiness).validate();
  }
}
