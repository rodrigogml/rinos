package br.com.rinos.app.backend.module.membership.service;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;

/** Ponte interna de membership para a autoridade de capacidade do módulo de planos. */
public interface MembershipPlanCapacityPort {

  MembershipPlanCapacityDecision evaluate(long accountId, long prospectiveUserId);

  default TenantUserCapacityResult reserve(
      long accountId,
      UUID invitationId,
      String normalizedEmail,
      Long prospectiveUserId,
      Instant requestedAt,
      Instant expiresAt,
      String correlationId) {
    MembershipPlanCapacityDecision decision = evaluate(
        accountId, prospectiveUserId == null ? Long.MAX_VALUE : prospectiveUserId);
    return compatible(decision, br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus.RESERVED);
  }

  default TenantUserCapacityResult convert(
      long accountId,
      long userId,
      UUID invitationId,
      String correlationId) {
    return compatible(evaluate(accountId, userId),
        br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus.OCCUPIED);
  }

  default TenantUserCapacityResult release(
      long accountId,
      UUID invitationId,
      String correlationId) {
    return new TenantUserCapacityResult(
        br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus.RELEASED,
        0, 0, 0, null);
  }

  default TenantUserCapacityResult occupy(
      long accountId,
      long userId,
      UUID intentionId,
      String correlationId) {
    return compatible(evaluate(accountId, userId),
        br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus.ALREADY_OCCUPIED);
  }

  private static TenantUserCapacityResult compatible(
      MembershipPlanCapacityDecision decision,
      br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus success) {
    if (!decision.sourceAvailable()) {
      return new TenantUserCapacityResult(
          br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus.SOURCE_UNAVAILABLE,
          0, 0, 0, "PLAN_SOURCE_UNAVAILABLE");
    }
    return decision.allowed()
        ? new TenantUserCapacityResult(success, 10, 0, 0, null)
        : new TenantUserCapacityResult(
            br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus.LIMIT_REACHED,
            10, 10, 0, "PLAN_LIMIT_REACHED");
  }
}
