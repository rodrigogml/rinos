package br.com.rinos.app.api.module.plans.dto;

import java.util.Objects;
import java.util.UUID;

/** Intenção idempotente de contrato pessoal derivada durante a ativação da identidade. */
public record PersonalContractBootstrapRequest(
    UUID protocolId,
    long userId,
    String correlationId) {

  public PersonalContractBootstrapRequest {
    protocolId = Objects.requireNonNull(protocolId, "protocolId must not be null");
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    correlationId = requireCorrelation(correlationId);
  }

  @Override
  public String toString() {
    return "PersonalContractBootstrapRequest[protocolId=REDACTED, userId=REDACTED, correlationId="
        + correlationId + "]";
  }

  private static String requireCorrelation(String value) {
    if (value == null || value.isBlank() || value.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    return value.strip();
  }
}
