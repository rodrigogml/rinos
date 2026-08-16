package br.com.rinos.app.api.module.access.keys;

import java.util.Set;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;

/** Operações humanas tipadas da central de acesso. */
public final class AccessControlOperations {

  private AccessControlOperations() {
  }

  public static AuthorizationOperation viewCatalog(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.catalog.global.view" : "access.catalog.tenant.view",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_CATALOG_VIEW
            : AccessControlAccessKeys.TENANT_CATALOG_VIEW), false);
  }

  public static AuthorizationOperation inspectViews(AccessScope scope) {
    return scope == AccessScope.GLOBAL
        ? new AuthorizationOperation("access.center.global.inspect", Set.of(
            AccessControlAccessKeys.GLOBAL_CATALOG_VIEW,
            AccessControlAccessKeys.GLOBAL_GROUP_VIEW,
            AccessControlAccessKeys.GLOBAL_RULE_VIEW), false)
        : new AuthorizationOperation("access.center.tenant.inspect", Set.of(
            AccessControlAccessKeys.TENANT_CATALOG_VIEW,
            AccessControlAccessKeys.TENANT_GROUP_VIEW,
            AccessControlAccessKeys.TENANT_RULE_VIEW), false);
  }

  public static AuthorizationOperation inspectManagement(AccessScope scope) {
    return scope == AccessScope.GLOBAL
        ? new AuthorizationOperation("access.center.global.manage.inspect", Set.of(
            AccessControlAccessKeys.GLOBAL_GROUP_MANAGE,
            AccessControlAccessKeys.GLOBAL_RULE_MANAGE), false)
        : new AuthorizationOperation("access.center.tenant.manage.inspect", Set.of(
            AccessControlAccessKeys.TENANT_GROUP_MANAGE,
            AccessControlAccessKeys.TENANT_RULE_MANAGE), false);
  }

  public static AuthorizationOperation viewGroup(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.group.global.view" : "access.group.tenant.view",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_GROUP_VIEW
            : AccessControlAccessKeys.TENANT_GROUP_VIEW), false);
  }

  public static AuthorizationOperation viewRule(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.rule.global.view" : "access.rule.tenant.view",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_RULE_VIEW
            : AccessControlAccessKeys.TENANT_RULE_VIEW), false);
  }

  public static AuthorizationOperation manageGroup(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.group.global.manage" : "access.group.tenant.manage",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_GROUP_MANAGE
            : AccessControlAccessKeys.TENANT_GROUP_MANAGE), true);
  }

  public static AuthorizationOperation manageRule(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.rule.global.manage" : "access.rule.tenant.manage",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_RULE_MANAGE
            : AccessControlAccessKeys.TENANT_RULE_MANAGE), true);
  }

  public static AuthorizationOperation previewGroup(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.group.global.preview" : "access.group.tenant.preview",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_GROUP_MANAGE
            : AccessControlAccessKeys.TENANT_GROUP_MANAGE), false);
  }

  public static AuthorizationOperation previewRule(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL ? "access.rule.global.preview" : "access.rule.tenant.preview",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_RULE_MANAGE
            : AccessControlAccessKeys.TENANT_RULE_MANAGE), false);
  }

  public static AuthorizationOperation inspectExplanation(AccessScope scope) {
    return new AuthorizationOperation(
        scope == AccessScope.GLOBAL
            ? "access.explanation.global.inspect" : "access.explanation.tenant.inspect",
        Set.of(scope == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_EXPLAIN : AccessControlAccessKeys.TENANT_EXPLAIN),
        false);
  }
}
