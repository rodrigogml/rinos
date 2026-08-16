package br.com.rinos.app.api.module.plans.dto;

import java.util.Objects;
import java.util.UUID;

/** Intenção idempotente de contrato tenant e ocupação da associação fundadora. */
public record TenantContractBootstrapRequest(
    UUID protocolId,
    UUID accountPublicId,
    UUID tenantPublicId,
    long founderUserId,
    String correlationId) {

  public TenantContractBootstrapRequest {
    protocolId = Objects.requireNonNull(protocolId, "protocolId must not be null");
    accountPublicId = Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    tenantPublicId = Objects.requireNonNull(tenantPublicId, "tenantPublicId must not be null");
    if (founderUserId <= 0) {
      throw new IllegalArgumentException("founderUserId must be positive");
    }
    if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    correlationId = correlationId.strip();
  }

  @Override
  public String toString() {
    return "TenantContractBootstrapRequest[protocolId=REDACTED, accountPublicId=REDACTED, "
        + "tenantPublicId=REDACTED, founderUserId=REDACTED, correlationId=" + correlationId + "]";
  }
}
