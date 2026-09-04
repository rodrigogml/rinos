package br.com.rinos.app.api.module.plans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.plans.dto.EntitlementEvaluationRequest;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.enums.EntitlementDecisionStatus;
import br.com.rinos.app.api.module.plans.vo.EntitlementDecision;
import br.com.rinos.app.api.module.plans.vo.EntitlementEvaluationResult;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.PersonalEntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;

class EntitlementContractsTest {

  private static final EntitlementRequirement TENANT_LIMIT = new EntitlementRequirement(
      ContractScope.TENANT, "membership.associated-users.limit");

  @Test
  void request_shouldRejectCrossScopeAndDefensivelyCopyRequirements() {
    Set<EntitlementRequirement> requirements = new HashSet<>(Set.of(TENANT_LIMIT));
    EntitlementEvaluationRequest request = new EntitlementEvaluationRequest(
        new TenantEntitlementSubject(42L), requirements, "tenant.membership.invite",
        Instant.parse("2026-08-16T18:00:00Z"), "correlation-1");
    requirements.clear();

    assertThat(request.requirements()).containsExactly(TENANT_LIMIT);
    assertThatThrownBy(() -> new EntitlementEvaluationRequest(
        new PersonalEntitlementSubject(11L), Set.of(TENANT_LIMIT), "personal.files.read",
        Instant.now(), "correlation-2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("scope");
  }

  @Test
  void decision_shouldRequireEveryRightAndKeepLimitDistinct() {
    EntitlementEvaluationResult available = new EntitlementEvaluationResult(
        TENANT_LIMIT, EntitlementDecisionStatus.AVAILABLE, 10L, 9L, false, null);
    EntitlementDecision permitted = new EntitlementDecision(
        new TenantEntitlementSubject(42L), List.of(available));
    EntitlementEvaluationResult reached = new EntitlementEvaluationResult(
        TENANT_LIMIT, EntitlementDecisionStatus.LIMIT_REACHED, 10L, 10L, false,
        "PLAN_LIMIT_REACHED");

    assertThat(permitted.allowed()).isTrue();
    assertThat(new EntitlementDecision(
        new TenantEntitlementSubject(42L), List.of(reached)).allowed()).isFalse();
    assertThatThrownBy(() -> new EntitlementEvaluationResult(
        TENANT_LIMIT, EntitlementDecisionStatus.AVAILABLE, 10L, null, false, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EntitlementDecision(
        new TenantEntitlementSubject(42L), List.of(available, available)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only once");
  }

  @Test
  void subjectToString_shouldRedactInternalIdentifiers() {
    assertThat(new PersonalEntitlementSubject(11L).toString()).doesNotContain("11");
    assertThat(new TenantEntitlementSubject(42L).toString()).doesNotContain("42");
  }
}
