package br.com.rinos.app.api.module.plans.dto;

import java.util.Objects;
import java.util.UUID;

/** Conversão atômica de reserva de convite em ocupação da identidade. */
public record InvitationAcceptanceCapacityRequest(
    long tenantId,
    long userId,
    UUID invitationId,
    String correlationId) {

  public InvitationAcceptanceCapacityRequest {
    if (tenantId <= 0 || userId <= 0) {
      throw new IllegalArgumentException("tenantId and userId must be positive");
    }
    invitationId = Objects.requireNonNull(invitationId, "invitationId must not be null");
    if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    correlationId = correlationId.strip();
  }
}
