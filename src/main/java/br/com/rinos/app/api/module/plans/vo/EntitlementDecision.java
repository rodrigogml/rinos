package br.com.rinos.app.api.module.plans.vo;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Decisão completa e imutável; todos os requisitos devem estar disponíveis. */
public record EntitlementDecision(
    EntitlementSubject subject,
    List<EntitlementEvaluationResult> results) {

  public EntitlementDecision {
    subject = Objects.requireNonNull(subject, "subject must not be null");
    results = results == null ? List.of() : List.copyOf(results);
    if (results.isEmpty()) {
      throw new IllegalArgumentException("results must not be empty");
    }
    var subjectScope = subject.scope();
    if (results.stream().anyMatch(result ->
        result.requirement().subjectScope() != subjectScope)) {
      throw new IllegalArgumentException("result scope is incompatible with subject");
    }
    if (new HashSet<>(results.stream()
        .map(EntitlementEvaluationResult::requirement)
        .toList()).size() != results.size()) {
      throw new IllegalArgumentException("results must contain each requirement only once");
    }
  }

  public boolean allowed() {
    return results.stream().allMatch(EntitlementEvaluationResult::allowed);
  }
}
