package br.com.rinos.app.api.module.access.vo;

/** Resultado seguro de uma condição externa às regras de chave. */
public record AuthorizationGateResult(String gateCode, boolean allowed, String safeReasonCode) {

  public AuthorizationGateResult {
    gateCode = requireText(gateCode, "gateCode");
    safeReasonCode = safeReasonCode == null ? null : requireText(safeReasonCode, "safeReasonCode");
    if (allowed && safeReasonCode != null) {
      throw new IllegalArgumentException("allowed gate must not have a denial reason");
    }
    if (!allowed && safeReasonCode == null) {
      throw new IllegalArgumentException("denied gate requires a safe reason");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.strip();
  }
}
