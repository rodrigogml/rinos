package br.com.rinos.app.backend.module.access.component;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import br.com.rinos.app.backend.module.access.service.AccessCatalogSynchronizationService;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;

class AccessCatalogReadinessRunnerTest {

  @Test
  void run_shouldSynchronizeCatalogBeforeReadiness() {
    AccessCatalogSynchronizationService synchronization =
        mock(AccessCatalogSynchronizationService.class);
    AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    AccessCatalogReadinessRunner runner =
        new AccessCatalogReadinessRunner(synchronization, authorization);

    runner.run(mock(ApplicationArguments.class));

    verify(authorization).require(org.mockito.ArgumentMatchers.any());
    verify(synchronization).synchronize();
  }
}
