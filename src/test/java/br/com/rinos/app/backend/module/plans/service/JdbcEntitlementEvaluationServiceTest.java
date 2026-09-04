package br.com.rinos.app.backend.module.plans.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;

import br.com.rinos.app.api.module.plans.dto.EntitlementEvaluationRequest;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.enums.EntitlementDecisionStatus;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;

class JdbcEntitlementEvaluationServiceTest {

  @Test
  void shouldFailClosedWhenContractSourceIsUnavailable() {
    JdbcOperations jdbc = mock(JdbcOperations.class);
    when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
        any(), any(), any()))
        .thenThrow(new DataAccessResourceFailureException("database unavailable"));
    JdbcEntitlementEvaluationService service = new JdbcEntitlementEvaluationService(
        jdbc, new PlanCompositionCache());
    EntitlementRequirement requirement = new EntitlementRequirement(
        ContractScope.TENANT, "membership.associated-users.limit");
    EntitlementEvaluationRequest request = new EntitlementEvaluationRequest(
        new TenantEntitlementSubject(1), Set.of(requirement), "membership.invite",
        Instant.now(), "plans-unit-test");

    var decision = service.evaluate(request);
    var accessSnapshot = service.inspect(request.subject(), request.requirements());

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.results().getFirst().status())
        .isEqualTo(EntitlementDecisionStatus.SOURCE_UNAVAILABLE);
    assertThat(decision.results().getFirst().safeReasonCode())
        .isEqualTo("PLAN_SOURCE_UNAVAILABLE");
    assertThat(accessSnapshot.sourceAvailable()).isFalse();
    assertThat(accessSnapshot.unavailableRequirements()).isEmpty();
  }
}
