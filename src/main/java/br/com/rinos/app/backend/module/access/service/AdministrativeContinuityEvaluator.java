package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.membership.service.MembershipContinuityDecision;

/** Avalia se um contexto preserva ao menos um administrador efetivamente apto. */
public interface AdministrativeContinuityEvaluator {

  AdministrativeContinuitySnapshot inspectContext(
      AccessScope scope, Long tenantId, Instant effectiveAt);

  default MembershipContinuityDecision evaluateContext(
      AccessScope scope, Long tenantId, Instant effectiveAt) {
    AdministrativeContinuitySnapshot snapshot = inspectContext(scope, tenantId, effectiveAt);
    return !snapshot.sourceAvailable() ? MembershipContinuityDecision.unavailable()
        : snapshot.allowed() ? MembershipContinuityDecision.permit()
        : MembershipContinuityDecision.deny();
  }
}
