package br.com.rinos.app.api.module.plans.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;

/** Snapshot seguro de capacidade; mutações continuam dependentes da transação autoritativa. */
public record TenantUserCapacityResult(
    TenantUserCapacityStatus status,
    long configuredLimit,
    long occupied,
    long reserved,
    String safeReasonCode) {

  public TenantUserCapacityResult {
    status = Objects.requireNonNull(status, "status must not be null");
    safeReasonCode = normalizeOptional(safeReasonCode);
    if (configuredLimit < 0 || occupied < 0 || reserved < 0) {
      throw new IllegalArgumentException("capacity values must not be negative");
    }
    boolean success = switch (status) {
      case AVAILABLE, RESERVED, OCCUPIED, ALREADY_RESERVED, ALREADY_OCCUPIED, RELEASED -> true;
      default -> false;
    };
    if (success == (safeReasonCode != null)) {
      throw new IllegalArgumentException("safeReasonCode is incompatible with status");
    }
  }

  public long used() {
    return Math.addExact(occupied, reserved);
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
