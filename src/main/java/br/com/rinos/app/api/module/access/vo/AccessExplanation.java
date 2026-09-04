package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;
import java.util.Objects;

/** Visão administrativa auditável e limitada ao contexto autorizado. */
public record AccessExplanation(
    AuthorizationDecision decision,
    String decisiveCondition,
    Instant generatedAt) {

  public AccessExplanation {
    decision = Objects.requireNonNull(decision, "decision must not be null");
    if (decisiveCondition == null || decisiveCondition.isBlank()) {
      throw new IllegalArgumentException("decisiveCondition must not be blank");
    }
    decisiveCondition = decisiveCondition.strip();
    generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    if (generatedAt.isBefore(decision.decidedAt())) {
      throw new IllegalArgumentException("generatedAt must not be before decidedAt");
    }
  }
}
