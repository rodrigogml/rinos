package br.com.rinos.app.api.module.plans.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.plans.enums.EntitlementDecisionStatus;

/** Resultado individual de um requisito, com quantidade opcional e motivo seguro. */
public record EntitlementEvaluationResult(
    EntitlementRequirement requirement,
    EntitlementDecisionStatus status,
    Long configuredLimit,
    Long observedUsage,
    boolean fallback,
    String safeReasonCode) {

  public EntitlementEvaluationResult {
    requirement = Objects.requireNonNull(requirement, "requirement must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    safeReasonCode = normalizeOptional(safeReasonCode);
    if (configuredLimit != null && configuredLimit < 0) {
      throw new IllegalArgumentException("configuredLimit must not be negative");
    }
    if (observedUsage != null && observedUsage < 0) {
      throw new IllegalArgumentException("observedUsage must not be negative");
    }
    if ((configuredLimit == null) != (observedUsage == null)) {
      throw new IllegalArgumentException("limit and usage must be informed together");
    }
    if (status == EntitlementDecisionStatus.AVAILABLE && safeReasonCode != null) {
      throw new IllegalArgumentException("available result must not carry a reason");
    }
    if (status != EntitlementDecisionStatus.AVAILABLE && safeReasonCode == null) {
      throw new IllegalArgumentException("non-available result requires a safe reason");
    }
  }

  public boolean allowed() {
    return status == EntitlementDecisionStatus.AVAILABLE;
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
