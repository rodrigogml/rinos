package br.com.rinos.app.api.module.access.keys;

import java.util.Set;

import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;

/** Operacoes tipadas da fundacao do tenant; papeis de ator nunca participam da decisao. */
public final class TenantFoundationOperations {

  private TenantFoundationOperations() {
  }

  public static AuthorizationOperation viewAccount() {
    return operation("tenant.account.view", false, InitialModuleAccessKeys.TENANT_ACCOUNT_VIEW);
  }

  public static AuthorizationOperation updateAccount() {
    return operation("tenant.account.update", true, InitialModuleAccessKeys.TENANT_ACCOUNT_UPDATE);
  }

  public static AuthorizationOperation manageAccountLifecycle() {
    return operation("tenant.account.lifecycle.manage", true,
        InitialModuleAccessKeys.TENANT_ACCOUNT_LIFECYCLE_MANAGE);
  }

  public static AuthorizationOperation viewMemberships() {
    return operation("tenant.membership.view", false,
        InitialModuleAccessKeys.TENANT_MEMBERSHIP_VIEW);
  }

  public static AuthorizationOperation inviteMembership() {
    return operation("tenant.membership.invite", false,
        InitialModuleAccessKeys.TENANT_MEMBERSHIP_INVITE);
  }

  public static AuthorizationOperation manageMembership(MembershipMutationOperation mutation) {
    if (mutation == null) throw new IllegalArgumentException("membership mutation must not be null");
    return operation("tenant.membership.manage." + mutation.name().toLowerCase(java.util.Locale.ROOT),
        true, InitialModuleAccessKeys.TENANT_MEMBERSHIP_MANAGE);
  }

  public static AuthorizationOperation viewPlan() {
    return operation("tenant.plan.view", false, InitialModuleAccessKeys.TENANT_PLAN_VIEW);
  }

  public static AuthorizationOperation viewAudit() {
    return operation("tenant.audit.view", false, InitialModuleAccessKeys.TENANT_AUDIT_VIEW);
  }

  private static AuthorizationOperation operation(
      String code, boolean sensitive,
      br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor key) {
    return new AuthorizationOperation(code, Set.of(key), sensitive);
  }
}
