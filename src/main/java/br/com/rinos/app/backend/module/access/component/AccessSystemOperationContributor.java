package br.com.rinos.app.backend.module.access.component;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.spi.SystemOperationContributor;
import br.com.rinos.app.api.module.access.vo.SystemOperationDescriptor;

/** Declara as operações autônomas pertencentes ao próprio módulo de acesso. */
@Component
public class AccessSystemOperationContributor implements SystemOperationContributor {
  public static final String CATALOG_ORIGIN = "ACCESS_CATALOG_READINESS";
  public static final String CATALOG_OPERATION = "access.catalog.synchronize";

  @Override
  public List<SystemOperationDescriptor> systemOperations() {
    return List.of(new SystemOperationDescriptor(
        CATALOG_ORIGIN, CATALOG_OPERATION, AccessScope.GLOBAL,
        Set.of(AccessControlAccessKeys.GLOBAL_CATALOG_MANAGE.code()), true));
  }
}
