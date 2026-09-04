package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;
import java.util.Objects;

/** Previa descartavel de uma mutacao ACL protegida. */
public record AccessAdministrationPreview(
    AuthorizationContext context,
    long revision,
    String proposedChangeCode,
    int eligibleAdministratorsBefore,
    int eligibleAdministratorsAfter,
    boolean protectedBaselineAffected,
    boolean confirmationAllowed,
    String safeReasonCode,
    Instant generatedAt) {

  public AccessAdministrationPreview {
    context = Objects.requireNonNull(context, "context must not be null");
    if (revision < 0 || eligibleAdministratorsBefore < 0 || eligibleAdministratorsAfter < 0) {
      throw new IllegalArgumentException("preview counters and revision must not be negative");
    }
    if (proposedChangeCode == null || proposedChangeCode.isBlank()) {
      throw new IllegalArgumentException("proposedChangeCode must not be blank");
    }
    proposedChangeCode = proposedChangeCode.strip();
    safeReasonCode = safeReasonCode == null || safeReasonCode.isBlank()
        ? null : safeReasonCode.strip();
    if (confirmationAllowed == (safeReasonCode != null)) {
      throw new IllegalArgumentException("preview reason is inconsistent with confirmation state");
    }
    generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
  }
}
