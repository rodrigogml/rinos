package br.com.rinos.app.backend.module.membership.component;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.api.module.plans.enums.TenantUserCapacityStatus;
import br.com.rinos.app.api.module.plans.vo.TenantUserCapacityResult;
import br.com.rinos.app.backend.module.membership.service.MembershipPlanCapacityDecision;
import br.com.rinos.app.backend.module.membership.service.MembershipPlanCapacityPort;

/** Fallback fechado quando a autoridade de planos não está disponível. */
public class UnavailableMembershipPlanCapacityAdapter implements MembershipPlanCapacityPort {

  @Override
  public MembershipPlanCapacityDecision evaluate(long accountId, long prospectiveUserId) {
    return MembershipPlanCapacityDecision.unavailable();
  }

  @Override
  public TenantUserCapacityResult reserve(
      long accountId, UUID invitationId, String normalizedEmail, Long prospectiveUserId,
      Instant requestedAt, Instant expiresAt, String correlationId) {
    return unavailable();
  }

  @Override
  public TenantUserCapacityResult convert(
      long accountId, long userId, UUID invitationId, String correlationId) {
    return unavailable();
  }

  @Override
  public TenantUserCapacityResult release(
      long accountId, UUID invitationId, String correlationId) {
    return unavailable();
  }

  @Override
  public TenantUserCapacityResult occupy(
      long accountId, long userId, UUID intentionId, String correlationId) {
    return unavailable();
  }

  private static TenantUserCapacityResult unavailable() {
    return new TenantUserCapacityResult(
        TenantUserCapacityStatus.SOURCE_UNAVAILABLE, 0, 0, 0, "PLAN_SOURCE_UNAVAILABLE");
  }
}
