package br.com.rinos.app.backend.module.plans.service;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.plans.enums.ContractScope;

/** Valida o catálogo mínimo publicado antes de liberar a aplicação. */
@Service
@ConditionalOnBean(DataSource.class)
public class PlansCatalogReadinessService {

  private static final String DEFAULT_VERSION_COUNT = """
      SELECT COUNT(*)
      FROM plans_plan plan
      JOIN plans_planVersion version
        ON version.idPlan = plan.idPlan AND version.scopeType = plan.scopeType
      WHERE plan.scopeType = ?
        AND plan.planCode = 'FREE'
        AND plan.status = 'ACTIVE'
        AND plan.defaultPlan = TRUE
        AND (plan.availableFrom IS NULL OR plan.availableFrom <= CURRENT_TIMESTAMP(6))
        AND (plan.availableUntil IS NULL OR plan.availableUntil > CURRENT_TIMESTAMP(6))
        AND version.status = 'PUBLISHED'
        AND (version.validFrom IS NULL OR version.validFrom <= CURRENT_TIMESTAMP(6))
        AND (version.validUntil IS NULL OR version.validUntil > CURRENT_TIMESTAMP(6))
      """;

  private static final String COMPOSITION_COUNT = """
      SELECT COUNT(*)
      FROM plans_plan plan
      JOIN plans_planVersion version
        ON version.idPlan = plan.idPlan AND version.scopeType = plan.scopeType
      JOIN plans_planVersionEntitlement composition
        ON composition.idPlanVersion = version.idPlanVersion
       AND composition.scopeType = version.scopeType
      WHERE plan.scopeType = ?
        AND plan.planCode = 'FREE'
        AND version.status = 'PUBLISHED'
        AND (version.validFrom IS NULL OR version.validFrom <= CURRENT_TIMESTAMP(6))
        AND (version.validUntil IS NULL OR version.validUntil > CURRENT_TIMESTAMP(6))
      """;

  private static final String REQUIRED_TENANT_LIMIT_COUNT = """
      SELECT COUNT(*)
      FROM plans_plan plan
      JOIN plans_planVersion version
        ON version.idPlan = plan.idPlan AND version.scopeType = plan.scopeType
      JOIN plans_planVersionEntitlement composition
        ON composition.idPlanVersion = version.idPlanVersion
       AND composition.scopeType = version.scopeType
      JOIN plans_entitlementDefinition definition
        ON definition.idEntitlementDefinition = composition.idEntitlementDefinition
       AND definition.scopeType = composition.scopeType
      WHERE plan.scopeType = 'TENANT'
        AND plan.planCode = 'FREE'
        AND version.status = 'PUBLISHED'
        AND (version.validFrom IS NULL OR version.validFrom <= CURRENT_TIMESTAMP(6))
        AND (version.validUntil IS NULL OR version.validUntil > CURRENT_TIMESTAMP(6))
        AND definition.entitlementCode = 'membership.associated-users.limit'
        AND definition.ownerModule = 'membership'
        AND definition.entitlementType = 'MAXIMUM_QUANTITY'
        AND definition.unitCode = 'DISTINCT_USER'
        AND definition.countingSemantics = 'EVER_ASSOCIATED'
        AND definition.status = 'ACTIVE'
        AND composition.booleanValue IS NULL
        AND composition.quantityValue = 10
        AND composition.periodCode IS NULL
      """;

  private final JdbcOperations jdbc;

  public PlansCatalogReadinessService(DataSource dataSource) {
    this(new JdbcTemplate(dataSource));
  }

  PlansCatalogReadinessService(JdbcOperations jdbc) {
    this.jdbc = jdbc;
  }

  /** Interrompe o startup quando o catálogo mínimo não é exatamente o esperado. */
  public void validate() {
    requireCount(DEFAULT_VERSION_COUNT, 1L, ContractScope.PERSONAL.name(),
        "personal default plan/version is unavailable or ambiguous");
    requireCount(DEFAULT_VERSION_COUNT, 1L, ContractScope.TENANT.name(),
        "tenant default plan/version is unavailable or ambiguous");
    requireCount(COMPOSITION_COUNT, 0L, ContractScope.PERSONAL.name(),
        "personal FREE composition must be empty");
    requireCount(COMPOSITION_COUNT, 1L, ContractScope.TENANT.name(),
        "tenant FREE composition must contain exactly one entitlement");
    requireCount(REQUIRED_TENANT_LIMIT_COUNT, 1L,
        "tenant associated-users limit is unavailable or inconsistent");
  }

  private void requireCount(String sql, long expected, String scope, String message) {
    Long actual = jdbc.queryForObject(sql, Long.class, scope);
    require(expected, actual, message);
  }

  private void requireCount(String sql, long expected, String message) {
    Long actual = jdbc.queryForObject(sql, Long.class);
    require(expected, actual, message);
  }

  private static void require(long expected, Long actual, String message) {
    if (actual == null || actual != expected) {
      throw new IllegalStateException(message);
    }
  }
}
