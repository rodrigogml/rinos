package br.com.rinos.app.api.module.plans.dto;

import java.util.Objects;
import java.util.UUID;

/** Liberação idempotente permitida somente para convite ainda não aceito. */
public record InvitationCapacityReleaseRequest(
    long tenantId,
    UUID invitationId,
    String correlationId) {

  public InvitationCapacityReleaseRequest {
    if (tenantId <= 0) {
      throw new IllegalArgumentException("tenantId must be positive");
    }
    invitationId = Objects.requireNonNull(invitationId, "invitationId must not be null");
    if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    correlationId = correlationId.strip();
  }
}
