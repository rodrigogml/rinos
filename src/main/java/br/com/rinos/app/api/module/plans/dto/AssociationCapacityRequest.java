package br.com.rinos.app.api.module.plans.dto;

import java.util.Objects;
import java.util.UUID;

/** Ocupação permanente de uma identidade associada ao tenant. */
public record AssociationCapacityRequest(
    long tenantId,
    long userId,
    UUID intentionId,
    String correlationId) {

  public AssociationCapacityRequest {
    if (tenantId <= 0 || userId <= 0) {
      throw new IllegalArgumentException("tenantId and userId must be positive");
    }
    intentionId = Objects.requireNonNull(intentionId, "intentionId must not be null");
    if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    correlationId = correlationId.strip();
  }

  @Override
  public String toString() {
    return "AssociationCapacityRequest[tenantId=REDACTED, userId=REDACTED, "
        + "intentionId=REDACTED, correlationId=" + correlationId + "]";
  }
}
