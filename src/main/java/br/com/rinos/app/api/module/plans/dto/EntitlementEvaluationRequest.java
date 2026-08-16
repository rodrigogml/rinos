package br.com.rinos.app.api.module.plans.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.EntitlementSubject;

/** Requisição canônica de direitos, independente da sessão e do contexto visual. */
public record EntitlementEvaluationRequest(
    EntitlementSubject subject,
    Set<EntitlementRequirement> requirements,
    String operationCode,
    Instant evaluatedAt,
    String correlationId) {

  public EntitlementEvaluationRequest {
    subject = Objects.requireNonNull(subject, "subject must not be null");
    requirements = requirements == null ? Set.of() : Set.copyOf(requirements);
    evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    operationCode = requireText(operationCode, "operationCode", 200);
    correlationId = requireText(correlationId, "correlationId", 100);
    if (requirements.isEmpty()) {
      throw new IllegalArgumentException("requirements must not be empty");
    }
    var subjectScope = subject.scope();
    if (requirements.stream().anyMatch(item -> item.subjectScope() != subjectScope)) {
      throw new IllegalArgumentException("requirement scope is incompatible with subject");
    }
  }

  @Override
  public String toString() {
    return "EntitlementEvaluationRequest[subject=" + subject.scope()
        + ", requirementCount=" + requirements.size() + ", operationCode=" + operationCode
        + ", evaluatedAt=" + evaluatedAt + ", correlationId=" + correlationId + "]";
  }

  private static String requireText(String value, String field, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength) {
      throw new IllegalArgumentException(field + " is invalid");
    }
    return value.strip();
  }
}
