package br.com.rinos.app.backend.module.access.component;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import javax.sql.DataSource;

import br.com.rinos.app.backend.module.access.service.AccessCatalogSynchronizationService;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import java.util.Set;

/** Sincroniza o catálogo depois das migrations e antes de a aplicação ficar pronta. */
@Component
@ConditionalOnBean(DataSource.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessCatalogReadinessRunner implements ApplicationRunner {

  private final AccessCatalogSynchronizationService synchronizationService;
  private final AuthorizationFacade authorization;

  public AccessCatalogReadinessRunner(
      AccessCatalogSynchronizationService synchronizationService,
      AuthorizationFacade authorization) {
    this.synchronizationService = synchronizationService;
    this.authorization = authorization;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    authorization.require(new AuthorizationRequest(
        AuthorizationActor.system(AccessSystemOperationContributor.CATALOG_ORIGIN), null,
        AuthorizationContext.global(), AccessSystemOperationContributor.CATALOG_OPERATION,
        Set.of(AccessControlAccessKeys.GLOBAL_CATALOG_MANAGE), null, false,
        AuthorizationExplanationMode.NONE));
    synchronizationService.synchronize();
  }
}
