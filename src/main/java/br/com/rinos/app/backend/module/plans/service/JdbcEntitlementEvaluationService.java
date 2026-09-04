package br.com.rinos.app.backend.module.plans.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.plans.dto.EntitlementEvaluationRequest;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.enums.EntitlementDecisionStatus;
import br.com.rinos.app.api.module.plans.enums.EntitlementType;
import br.com.rinos.app.api.module.plans.facade.EntitlementEvaluationFacade;
import br.com.rinos.app.api.module.plans.vo.EntitlementDecision;
import br.com.rinos.app.api.module.plans.vo.EntitlementEvaluationResult;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.EntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.PersonalEntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessPort;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessSnapshot;
import br.com.rinos.app.backend.module.plans.service.PlanCompositionCache.PublishedEntitlement;

/** Avalia contrato e atribuição em tempo de execução, cacheando apenas composição publicada. */
@Service
@ConditionalOnBean(DataSource.class)
public class JdbcEntitlementEvaluationService
    implements EntitlementEvaluationFacade, PlanEntitlementAccessPort {

  private static final String CONTRACT_PERSONAL_SQL = """
      SELECT c.idServiceContract
        FROM plans_personalContractHolder h
        JOIN plans_serviceContract c ON c.idServiceContract = h.idServiceContract
       WHERE h.idUser = ? AND c.scopeType = 'PERSONAL' AND c.status = 'ACTIVE'
         AND c.startedAt <= ? AND (c.endedAt IS NULL OR c.endedAt > ?)
      """;
  private static final String CONTRACT_TENANT_SQL = """
      SELECT c.idServiceContract
        FROM plans_tenantContractHolder h
        JOIN plans_serviceContract c ON c.idServiceContract = h.idServiceContract
       WHERE h.idTenant = ? AND c.scopeType = 'TENANT' AND c.status = 'ACTIVE'
         AND c.startedAt <= ? AND (c.endedAt IS NULL OR c.endedAt > ?)
      """;
  private static final String ASSIGNMENT_SQL = """
      SELECT pv.idPlanVersion
        FROM plans_planAssignment a
        JOIN plans_planVersion pv ON pv.idPlanVersion = a.idPlanVersion
       WHERE a.idServiceContract = ? AND a.scopeType = ? AND a.status = 'ACTIVE'
         AND a.startedAt <= ? AND (a.endedAt IS NULL OR a.endedAt > ?)
         AND pv.scopeType = ? AND pv.status = 'PUBLISHED'
         AND (pv.validFrom IS NULL OR pv.validFrom <= ?)
         AND (pv.validUntil IS NULL OR pv.validUntil > ?)
      """;
  private static final String FALLBACK_SQL = """
      SELECT pv.idPlanVersion
        FROM plans_plan p
        JOIN plans_planVersion pv ON pv.idPlan = p.idPlan AND pv.scopeType = p.scopeType
       WHERE p.scopeType = ? AND p.defaultPlan = TRUE AND p.status = 'ACTIVE'
         AND (p.availableFrom IS NULL OR p.availableFrom <= ?)
         AND (p.availableUntil IS NULL OR p.availableUntil > ?)
         AND pv.status = 'PUBLISHED'
         AND (pv.validFrom IS NULL OR pv.validFrom <= ?)
         AND (pv.validUntil IS NULL OR pv.validUntil > ?)
       ORDER BY pv.versionNumber DESC
      """;
  private static final String COMPOSITION_SQL = """
      SELECT d.entitlementCode, d.entitlementType,
             e.booleanValue, e.quantityValue, e.periodCode
        FROM plans_planVersionEntitlement e
        JOIN plans_entitlementDefinition d
          ON d.idEntitlementDefinition = e.idEntitlementDefinition
         AND d.scopeType = e.scopeType
        JOIN plans_planVersion pv
          ON pv.idPlanVersion = e.idPlanVersion AND pv.scopeType = e.scopeType
       WHERE e.idPlanVersion = ? AND pv.status = 'PUBLISHED' AND d.status = 'ACTIVE'
      """;
  private static final String TENANT_USAGE_SQL = """
      SELECT
        (SELECT COUNT(*) FROM plans_tenantUserCapacityOccupancy o
          WHERE o.idServiceContract = ?) +
        (SELECT COUNT(*) FROM plans_tenantUserCapacityReservation r
          WHERE r.idServiceContract = ? AND r.capacityMarker = 1 AND r.expiresAt > ?)
      """;

  private final JdbcOperations jdbc;
  private final PlanCompositionCache cache;

  @Autowired
  public JdbcEntitlementEvaluationService(DataSource dataSource) {
    this(new JdbcTemplate(dataSource), new PlanCompositionCache());
  }

  JdbcEntitlementEvaluationService(JdbcOperations jdbc, PlanCompositionCache cache) {
    this.jdbc = jdbc;
    this.cache = cache;
  }

  @Override
  public EntitlementDecision evaluate(EntitlementEvaluationRequest request) {
    try {
      Resolution resolution = resolve(request.subject(), request.evaluatedAt());
      if (resolution == null) {
        return decisionForAll(request, EntitlementDecisionStatus.UNAVAILABLE,
            "PLAN_CONTRACT_UNAVAILABLE");
      }
      Map<String, PublishedEntitlement> composition = cache.get(
          resolution.planVersionId(), this::loadComposition);
      List<EntitlementEvaluationResult> results = request.requirements().stream()
          .sorted(Comparator.comparing(EntitlementRequirement::code))
          .map(requirement -> evaluateOne(
              requirement, request.subject(), resolution, composition, request.evaluatedAt()))
          .toList();
      return new EntitlementDecision(request.subject(), results);
    } catch (DataAccessException | IllegalStateException exception) {
      return decisionForAll(request, EntitlementDecisionStatus.SOURCE_UNAVAILABLE,
          "PLAN_SOURCE_UNAVAILABLE");
    }
  }

  @Override
  public PlanEntitlementAccessSnapshot inspect(
      EntitlementSubject subject,
      Set<EntitlementRequirement> requirements) {
    EntitlementDecision decision = evaluate(new EntitlementEvaluationRequest(
        subject, requirements, "access-control", Instant.now(), "access-control"));
    boolean sourceAvailable = decision.results().stream()
        .noneMatch(result -> result.status() == EntitlementDecisionStatus.SOURCE_UNAVAILABLE);
    if (!sourceAvailable) {
      return PlanEntitlementAccessSnapshot.unavailable();
    }
    Set<EntitlementRequirement> unavailable = decision.results().stream()
        .filter(result -> !result.allowed())
        .map(EntitlementEvaluationResult::requirement)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return PlanEntitlementAccessSnapshot.available(unavailable);
  }

  private Resolution resolve(EntitlementSubject subject, Instant at) {
    ContractScope scope = subject.scope();
    long subjectId = switch (subject) {
      case PersonalEntitlementSubject personal -> personal.userId();
      case TenantEntitlementSubject tenant -> tenant.tenantId();
    };
    Timestamp instant = Timestamp.from(at);
    String contractSql = scope == ContractScope.PERSONAL
        ? CONTRACT_PERSONAL_SQL : CONTRACT_TENANT_SQL;
    List<Long> contracts = jdbc.query(
        contractSql, (row, number) -> row.getLong(1), subjectId, instant, instant);
    if (contracts.size() != 1) {
      return null;
    }
    long contractId = contracts.getFirst();
    List<Long> assignments = jdbc.query(ASSIGNMENT_SQL,
        (row, number) -> row.getLong(1), contractId, scope.name(), instant, instant,
        scope.name(), instant, instant);
    if (assignments.size() > 1) {
      throw new IllegalStateException("multiple effective plan assignments");
    }
    if (assignments.size() == 1) {
      return new Resolution(contractId, assignments.getFirst(), false);
    }
    List<Long> fallbacks = jdbc.query(FALLBACK_SQL,
        (row, number) -> row.getLong(1), scope.name(), instant, instant, instant, instant);
    if (fallbacks.size() != 1) {
      return null;
    }
    return new Resolution(contractId, fallbacks.getFirst(), true);
  }

  private Map<String, PublishedEntitlement> loadComposition(long planVersionId) {
    Map<String, PublishedEntitlement> composition = new LinkedHashMap<>();
    jdbc.query(COMPOSITION_SQL, resultSet -> {
      PublishedEntitlement entitlement = mapEntitlement(resultSet);
      String code = resultSet.getString("entitlementCode");
      if (composition.put(code, entitlement) != null) {
        throw new IllegalStateException("duplicate entitlement in published composition");
      }
    }, planVersionId);
    return composition;
  }

  private EntitlementEvaluationResult evaluateOne(
      EntitlementRequirement requirement,
      EntitlementSubject subject,
      Resolution resolution,
      Map<String, PublishedEntitlement> composition,
      Instant evaluatedAt) {
    PublishedEntitlement entitlement = composition.get(requirement.code());
    if (entitlement == null) {
      return unavailable(requirement, resolution.fallback(), "PLAN_ENTITLEMENT_UNAVAILABLE");
    }
    return switch (entitlement.type()) {
      case AVAILABILITY -> Boolean.TRUE.equals(entitlement.booleanValue())
          ? available(requirement, resolution.fallback(), null, null)
          : unavailable(requirement, resolution.fallback(), "PLAN_ENTITLEMENT_UNAVAILABLE");
      case MAXIMUM_QUANTITY -> evaluateQuantity(
          requirement, subject, resolution, entitlement, evaluatedAt);
      case PERIODIC_QUOTA -> sourceUnavailable(requirement, resolution.fallback());
    };
  }

  private EntitlementEvaluationResult evaluateQuantity(
      EntitlementRequirement requirement,
      EntitlementSubject subject,
      Resolution resolution,
      PublishedEntitlement entitlement,
      Instant evaluatedAt) {
    if (!(subject instanceof TenantEntitlementSubject)
        || !"membership.associated-users.limit".equals(requirement.code())
        || entitlement.quantityValue() == null) {
      return sourceUnavailable(requirement, resolution.fallback());
    }
    Long usage = jdbc.queryForObject(TENANT_USAGE_SQL, Long.class,
        resolution.contractId(), resolution.contractId(), Timestamp.from(evaluatedAt));
    if (usage == null) {
      throw new IllegalStateException("capacity usage unavailable");
    }
    EntitlementDecisionStatus status = usage < entitlement.quantityValue()
        ? EntitlementDecisionStatus.AVAILABLE : EntitlementDecisionStatus.LIMIT_REACHED;
    return new EntitlementEvaluationResult(requirement, status,
        entitlement.quantityValue(), usage, resolution.fallback(),
        status == EntitlementDecisionStatus.AVAILABLE ? null : "PLAN_LIMIT_REACHED");
  }

  private static PublishedEntitlement mapEntitlement(ResultSet row) throws SQLException {
    Boolean booleanValue = (Boolean) row.getObject("booleanValue");
    Long quantityValue = row.getObject("quantityValue") == null
        ? null : row.getLong("quantityValue");
    return new PublishedEntitlement(
        EntitlementType.valueOf(row.getString("entitlementType")),
        booleanValue, quantityValue, row.getString("periodCode"));
  }

  private static EntitlementDecision decisionForAll(
      EntitlementEvaluationRequest request,
      EntitlementDecisionStatus status,
      String reason) {
    return new EntitlementDecision(request.subject(), request.requirements().stream()
        .sorted(Comparator.comparing(EntitlementRequirement::code))
        .map(requirement -> new EntitlementEvaluationResult(
            requirement, status, null, null, false, reason))
        .toList());
  }

  private static EntitlementEvaluationResult available(
      EntitlementRequirement requirement, boolean fallback, Long limit, Long usage) {
    return new EntitlementEvaluationResult(requirement, EntitlementDecisionStatus.AVAILABLE,
        limit, usage, fallback, null);
  }

  private static EntitlementEvaluationResult unavailable(
      EntitlementRequirement requirement, boolean fallback, String reason) {
    return new EntitlementEvaluationResult(requirement, EntitlementDecisionStatus.UNAVAILABLE,
        null, null, fallback, reason);
  }

  private static EntitlementEvaluationResult sourceUnavailable(
      EntitlementRequirement requirement, boolean fallback) {
    return new EntitlementEvaluationResult(requirement,
        EntitlementDecisionStatus.SOURCE_UNAVAILABLE,
        null, null, fallback, "PLAN_SOURCE_UNAVAILABLE");
  }

  private record Resolution(long contractId, long planVersionId, boolean fallback) {
  }
}
