package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Decisão completa, imutável e segura de uma operação protegida. */
public record AuthorizationDecision(
    boolean allowed,
    AuthorizationContext context,
    List<AuthorizationKeyResult> keyResults,
    List<AuthorizationGateResult> structuralGates,
    List<AuthorizationGateResult> entitlementGates,
    List<AuthorizationGateResult> assuranceGates,
    Set<String> safeReasonCodes,
    long revision,
    Instant decidedAt,
    UUID correlationId) {

  public AuthorizationDecision {
    context = Objects.requireNonNull(context, "context must not be null");
    keyResults = keyResults == null ? List.of() : List.copyOf(keyResults);
    structuralGates = structuralGates == null ? List.of() : List.copyOf(structuralGates);
    entitlementGates = entitlementGates == null ? List.of() : List.copyOf(entitlementGates);
    assuranceGates = assuranceGates == null ? List.of() : List.copyOf(assuranceGates);
    safeReasonCodes = safeReasonCodes == null ? Set.of() : Set.copyOf(safeReasonCodes);
    decidedAt = Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
    if (revision < 0 || context.contextRevision() == null
        || context.contextRevision().longValue() != revision) {
      throw new IllegalArgumentException("decision revision is inconsistent with context");
    }
    boolean computedAllowed = !keyResults.isEmpty()
        && keyResults.stream().allMatch(AuthorizationKeyResult::allowed)
        && allAllowed(structuralGates)
        && allAllowed(entitlementGates)
        && allAllowed(assuranceGates);
    if (allowed != computedAllowed) {
      throw new IllegalArgumentException("decision result is inconsistent with key and gate results");
    }
    if (allowed && !safeReasonCodes.isEmpty()) {
      throw new IllegalArgumentException("allowed decision must not have denial reasons");
    }
    if (!allowed && safeReasonCodes.isEmpty()) {
      throw new IllegalArgumentException("denied decision requires a safe reason");
    }
  }

  private static boolean allAllowed(List<AuthorizationGateResult> gates) {
    return gates.stream().allMatch(AuthorizationGateResult::allowed);
  }
}
