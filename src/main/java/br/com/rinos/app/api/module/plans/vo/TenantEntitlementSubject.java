package br.com.rinos.app.api.module.plans.vo;

import br.com.rinos.app.api.module.plans.enums.ContractScope;

/** Tenant titular de contrato organizacional. */
public record TenantEntitlementSubject(long tenantId) implements EntitlementSubject {

  public TenantEntitlementSubject {
    if (tenantId <= 0) {
      throw new IllegalArgumentException("tenantId must be positive");
    }
  }

  @Override
  public ContractScope scope() {
    return ContractScope.TENANT;
  }

  @Override
  public String toString() {
    return "TenantEntitlementSubject[tenantId=REDACTED]";
  }
}
