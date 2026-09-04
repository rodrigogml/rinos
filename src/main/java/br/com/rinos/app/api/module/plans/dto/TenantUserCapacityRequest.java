package br.com.rinos.app.api.module.plans.dto;

/** Consulta não autoritativa da capacidade atual de usuários do tenant. */
public record TenantUserCapacityRequest(long tenantId, Long prospectiveUserId) {

  public TenantUserCapacityRequest {
    if (tenantId <= 0 || (prospectiveUserId != null && prospectiveUserId <= 0)) {
      throw new IllegalArgumentException("tenantId and prospectiveUserId are invalid");
    }
  }
}
