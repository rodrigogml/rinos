package br.com.rinos.app.api.module.plans.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Reserva de capacidade antes do envio, sem transportar e-mail em claro. */
public record InvitationCapacityRequest(
    long tenantId,
    UUID invitationId,
    String recipientFingerprint,
    Long prospectiveUserId,
    Instant requestedAt,
    Instant expiresAt,
    String correlationId) {

  public InvitationCapacityRequest {
    if (tenantId <= 0 || (prospectiveUserId != null && prospectiveUserId <= 0)) {
      throw new IllegalArgumentException("tenantId and prospectiveUserId are invalid");
    }
    invitationId = Objects.requireNonNull(invitationId, "invitationId must not be null");
    requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (!expiresAt.isAfter(requestedAt)) {
      throw new IllegalArgumentException("expiresAt must be after requestedAt");
    }
    recipientFingerprint = requireText(recipientFingerprint, "recipientFingerprint", 200);
    correlationId = requireText(correlationId, "correlationId", 100);
  }

  @Override
  public String toString() {
    return "InvitationCapacityRequest[tenantId=REDACTED, invitationId=REDACTED, "
        + "recipientFingerprint=REDACTED, prospectiveUserId=REDACTED, requestedAt=" + requestedAt
        + ", expiresAt=" + expiresAt
        + ", correlationId=" + correlationId + "]";
  }

  private static String requireText(String value, String field, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return value.strip();
  }
}
