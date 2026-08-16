package br.com.rinos.app.api.module.plans.vo;

import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;

/** Resultado seguro do bootstrap de contrato e atribuição inicial. */
public record ContractBootstrapResult(
    ContractBootstrapStatus status,
    ContractScope scope,
    UUID contractPublicId,
    String safeReasonCode) {

  public ContractBootstrapResult {
    status = Objects.requireNonNull(status, "status must not be null");
    scope = Objects.requireNonNull(scope, "scope must not be null");
    safeReasonCode = normalizeOptional(safeReasonCode);
    boolean completed = status == ContractBootstrapStatus.COMPLETED
        || status == ContractBootstrapStatus.ALREADY_COMPLETED;
    if (completed && (contractPublicId == null || safeReasonCode != null)) {
      throw new IllegalArgumentException("completed result requires only contractPublicId");
    }
    if (!completed && (contractPublicId != null || safeReasonCode == null)) {
      throw new IllegalArgumentException("failed result requires only safeReasonCode");
    }
  }

  @Override
  public String toString() {
    return "ContractBootstrapResult[status=" + status + ", scope=" + scope
        + ", contractPublicId=REDACTED, safeReasonCode=" + safeReasonCode + "]";
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
