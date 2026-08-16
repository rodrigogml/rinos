package br.com.rinos.app.api.module.access.keys;

import java.util.Set;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyRequirement;

/** Descriptors tipados pertencentes ao próprio módulo de controle de acesso. */
public final class AccessControlAccessKeys {

  public static final AccessKeyDescriptor GLOBAL_CATALOG_VIEW = global(
      "global.access.catalog.view", "global.platform.access", requirements("KEY", 1, 12));
  public static final AccessKeyDescriptor GLOBAL_CATALOG_MANAGE = global(
      "global.access.catalog.manage", "global.platform.access", selectedRequirements("ADM", 1, 6));
  public static final AccessKeyDescriptor GLOBAL_GROUP_VIEW = global(
      "global.access.group.view", "global.platform.access", requirements("GRP", 1, 8));
  public static final AccessKeyDescriptor GLOBAL_GROUP_MANAGE = global(
      "global.access.group.manage", "global.platform.access", requirements("GRP", 1, 8));
  public static final AccessKeyDescriptor GLOBAL_RULE_VIEW = global(
      "global.access.rule.view", "global.platform.access", requirements("RULE", 1, 10));
  public static final AccessKeyDescriptor GLOBAL_RULE_MANAGE = global(
      "global.access.rule.manage", "global.platform.access", requirements("RULE", 1, 10));
  public static final AccessKeyDescriptor GLOBAL_EXPLAIN = global(
      "global.access.explain", "global.platform.access", requirements("EXP", 1, 6));

  public static final AccessKeyDescriptor TENANT_CATALOG_VIEW = tenant(
      "tenant.access.catalog.view", "tenant.foundation.access", requirements("KEY", 1, 12));
  public static final AccessKeyDescriptor TENANT_GROUP_VIEW = tenant(
      "tenant.access.group.view", "tenant.foundation.access", requirements("GRP", 1, 8));
  public static final AccessKeyDescriptor TENANT_GROUP_MANAGE = tenant(
      "tenant.access.group.manage", "tenant.foundation.access", requirements("GRP", 1, 8));
  public static final AccessKeyDescriptor TENANT_RULE_VIEW = tenant(
      "tenant.access.rule.view", "tenant.foundation.access", requirements("RULE", 1, 10));
  public static final AccessKeyDescriptor TENANT_RULE_MANAGE = tenant(
      "tenant.access.rule.manage", "tenant.foundation.access", requirements("RULE", 1, 10));
  public static final AccessKeyDescriptor TENANT_EXPLAIN = tenant(
      "tenant.access.explain", "tenant.foundation.access", requirements("EXP", 1, 6));

  public static final Set<AccessKeyDescriptor> ALL = Set.of(
      GLOBAL_CATALOG_VIEW,
      GLOBAL_CATALOG_MANAGE,
      GLOBAL_GROUP_VIEW,
      GLOBAL_GROUP_MANAGE,
      GLOBAL_RULE_VIEW,
      GLOBAL_RULE_MANAGE,
      GLOBAL_EXPLAIN,
      TENANT_CATALOG_VIEW,
      TENANT_GROUP_VIEW,
      TENANT_GROUP_MANAGE,
      TENANT_RULE_VIEW,
      TENANT_RULE_MANAGE,
      TENANT_EXPLAIN);

  private AccessControlAccessKeys() {
  }

  private static AccessKeyDescriptor global(
      String code, String categoryCode, Set<AccessKeyRequirement> requirements) {
    return AccessKeyDescriptor.active(
        code, AccessScope.GLOBAL, categoryCode, "access-control", requirements, true);
  }

  private static AccessKeyDescriptor tenant(
      String code, String categoryCode, Set<AccessKeyRequirement> requirements) {
    return AccessKeyDescriptor.active(
        code, AccessScope.TENANT, categoryCode, "access-control", requirements, true);
  }

  private static Set<AccessKeyRequirement> requirements(String group, int first, int last) {
    java.util.LinkedHashSet<AccessKeyRequirement> requirements = new java.util.LinkedHashSet<>();
    for (int index = first; index <= last; index++) {
      requirements.add(new AccessKeyRequirement(
          "access-control", "FR-ACL-" + group + "-" + String.format("%03d", index)));
    }
    return Set.copyOf(requirements);
  }

  private static Set<AccessKeyRequirement> selectedRequirements(String group, int... numbers) {
    java.util.LinkedHashSet<AccessKeyRequirement> requirements = new java.util.LinkedHashSet<>();
    for (int number : numbers) {
      requirements.add(new AccessKeyRequirement(
          "access-control", "FR-ACL-" + group + "-" + String.format("%03d", number)));
    }
    return Set.copyOf(requirements);
  }
}
