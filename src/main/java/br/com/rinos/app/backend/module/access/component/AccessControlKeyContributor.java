package br.com.rinos.app.backend.module.access.component;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.module.access.contributor.AccessKeyContributor;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;

/** Publica o catálogo pertencente ao núcleo de controle de acesso. */
@Component
public class AccessControlKeyContributor implements AccessKeyContributor {

  private static final List<AccessCategoryDescriptor> CATEGORIES = List.of(
      category("global.platform", null, AccessScope.GLOBAL),
      category("global.platform.directory", "global.platform", AccessScope.GLOBAL),
      category("global.platform.access", "global.platform", AccessScope.GLOBAL),
      category("global.platform.operations", "global.platform", AccessScope.GLOBAL),
      category("global.platform.commercial", "global.platform", AccessScope.GLOBAL),
      category("tenant.foundation", null, AccessScope.TENANT),
      category("tenant.foundation.access", "tenant.foundation", AccessScope.TENANT),
      category("tenant.parties", null, AccessScope.TENANT),
      category("tenant.financial", null, AccessScope.TENANT),
      category("tenant.financial.structure", "tenant.financial", AccessScope.TENANT),
      category("tenant.financial.operations", "tenant.financial", AccessScope.TENANT),
      category("tenant.financial.control", "tenant.financial", AccessScope.TENANT));

  @Override
  public String moduleCode() {
    return "access-control";
  }

  @Override
  public Collection<AccessKeyDescriptor> accessKeys() {
    return AccessControlAccessKeys.ALL;
  }

  @Override
  public Collection<AccessCategoryDescriptor> categories() {
    return CATEGORIES;
  }

  private static AccessCategoryDescriptor category(
      String code, String parentCode, AccessScope scope) {
    return new AccessCategoryDescriptor(code, parentCode, scope, "access.category." + code + ".name");
  }
}
