package br.com.rinos.app.backend.module.access.facade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;

import br.com.rinos.app.api.module.access.dto.AccessExplanationRequest;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessExplanation;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;
import br.com.rinos.app.backend.module.access.service.AccessDecisionAuditService;
import br.com.rinos.app.backend.module.access.service.AccessRuleResolutionService;
import br.com.rinos.app.backend.module.access.service.AuthorizationAssuranceGateProvider;
import br.com.rinos.app.backend.module.access.service.AuthorizationEntitlementGateProvider;
import br.com.rinos.app.backend.module.access.service.AuthorizationStructuralGateProvider;
import br.com.rinos.app.backend.module.access.service.ResolvedAccessSnapshot;
import br.com.rinos.app.backend.module.access.service.SystemOperationAuthorizer;

/** Implementação única da decisão, exigência e explicação de autorização. */
@Service
@Lazy
public class AuthorizationFacadeImpl implements AuthorizationFacade {

  private final AccessContextRevisionService revisionService;
  private final AccessRuleResolutionService ruleResolutionService;
  private final AuthorizationStructuralGateProvider structuralProvider;
  private final AuthorizationEntitlementGateProvider entitlementProvider;
  private final AuthorizationAssuranceGateProvider assuranceProvider;
  private final SystemOperationAuthorizer systemOperations;
  private final AccessDecisionAuditService decisionAudit;

  public AuthorizationFacadeImpl(
      AccessContextRevisionService revisionService,
      AccessRuleResolutionService ruleResolutionService,
      AuthorizationStructuralGateProvider structuralProvider,
      AuthorizationEntitlementGateProvider entitlementProvider,
      AuthorizationAssuranceGateProvider assuranceProvider,
      SystemOperationAuthorizer systemOperations,
      AccessDecisionAuditService decisionAudit) {
    this.revisionService = revisionService;
    this.ruleResolutionService = ruleResolutionService;
    this.structuralProvider = structuralProvider;
    this.entitlementProvider = entitlementProvider;
    this.assuranceProvider = assuranceProvider;
    this.systemOperations = systemOperations;
    this.decisionAudit = decisionAudit;
  }

  @Override
  public AuthorizationDecision decide(AuthorizationRequest request) {
    Instant decidedAt = Instant.now();
    UUID correlationId = UUID.randomUUID();
    List<AuthorizationGateResult> structural = safeEvaluate(
        () -> structuralProvider.evaluate(request), "ACL_STRUCTURAL_UNAVAILABLE");
    List<AuthorizationGateResult> entitlement = safeEvaluate(
        () -> entitlementProvider.evaluate(request), "ACL_PLAN_UNAVAILABLE");
    List<AuthorizationGateResult> assurance = safeEvaluate(
        () -> assuranceProvider.evaluate(request), "ACL_ASSURANCE_UNAVAILABLE");

    long revision = readRevision(request);
    List<AuthorizationKeyResult> keyResults;
    if (structural.stream().allMatch(AuthorizationGateResult::allowed)) {
      try {
        ResolvedAccessSnapshot snapshot = request.actor().type() == AuthorizationActorType.HUMAN
            ? ruleResolutionService.resolveHuman(request, decidedAt)
            : systemOperations.resolve(request, decidedAt);
        revision = snapshot.contextRevision();
        keyResults = snapshot.keyResults();
      } catch (RuntimeException exception) {
        keyResults = missingResults(request);
        structural = appendDenied(structural, "ACL_DECISION_UNAVAILABLE");
      }
    } else {
      keyResults = missingResults(request);
    }

    Set<String> reasons = new HashSet<>();
    structural.stream().filter(gate -> !gate.allowed()).map(AuthorizationGateResult::safeReasonCode)
        .forEach(reasons::add);
    entitlement.stream().filter(gate -> !gate.allowed()).map(AuthorizationGateResult::safeReasonCode)
        .forEach(reasons::add);
    assurance.stream().filter(gate -> !gate.allowed()).map(AuthorizationGateResult::safeReasonCode)
        .forEach(reasons::add);
    keyResults.forEach(result -> {
      if (!result.allowed()) {
        reasons.add(result.blockingSources().isEmpty()
            ? "ACL_KEY_MISSING" : "ACL_KEY_BLOCKED");
      }
    });
    if (reasons.isEmpty() && keyResults.stream().anyMatch(result -> !result.allowed())) {
      reasons.add("ACL_DENIED");
    }
    boolean allowed = keyResults.stream().allMatch(AuthorizationKeyResult::allowed)
        && structural.stream().allMatch(AuthorizationGateResult::allowed)
        && entitlement.stream().allMatch(AuthorizationGateResult::allowed)
        && assurance.stream().allMatch(AuthorizationGateResult::allowed);
    AuthorizationDecision decision = new AuthorizationDecision(
        allowed, request.context().withRevision(revision), keyResults, structural, entitlement,
        assurance, allowed ? Set.of() : reasons, revision, decidedAt, correlationId);
    decisionAudit.recordDeniedIfSensitive(request, decision);
    return decision;
  }

  @Override
  public AuthorizationDecision require(AuthorizationRequest request) {
    AuthorizationDecision decision = decide(request);
    if (!decision.allowed()) {
      throw new IllegalStateException("authorization denied: "
          + String.join(",", decision.safeReasonCodes()));
    }
    return decision;
  }

  @Override
  public AccessExplanation explain(AccessExplanationRequest request) {
    AuthorizationRequest target = request.targetRequest();
    AuthorizationRequest visibilityRequest = new AuthorizationRequest(
        request.requester(), request.requesterMembershipId(), target.context(),
        "access.explanation.view",
        Set.of(target.context().scope() == AccessScope.GLOBAL
            ? AccessControlAccessKeys.GLOBAL_EXPLAIN
            : AccessControlAccessKeys.TENANT_EXPLAIN),
        request.requesterAssurance(), true, AuthorizationExplanationMode.NONE);
    AuthorizationDecision visibility = decide(visibilityRequest);
    if (!visibility.allowed()
        || target.explanationMode() != AuthorizationExplanationMode.ADMINISTRATIVE) {
      throw new IllegalStateException("ACL_EXPLANATION_FORBIDDEN");
    }
    AuthorizationDecision decision = decide(target);
    return new AccessExplanation(decision, decisiveCondition(decision), Instant.now());
  }

  private long readRevision(AuthorizationRequest request) {
    try {
      return revisionService.current(request.context().scope(), request.context().tenantId());
    } catch (RuntimeException exception) {
      return 0L;
    }
  }

  private static List<AuthorizationKeyResult> missingResults(AuthorizationRequest request) {
    return request.requiredKeys().stream().map(key -> new AuthorizationKeyResult(
        key, false, List.of(), List.of(), true, List.of())).toList();
  }

  private static List<AuthorizationGateResult> appendDenied(
      List<AuthorizationGateResult> gates, String reason) {
    List<AuthorizationGateResult> result = new ArrayList<>(gates);
    result.add(new AuthorizationGateResult("ACL_RESOLUTION", false, reason));
    return result;
  }

  private static List<AuthorizationGateResult> safeEvaluate(
      java.util.function.Supplier<List<AuthorizationGateResult>> supplier,
      String fallbackReason) {
    try {
      List<AuthorizationGateResult> gates = supplier.get();
      if (gates == null || gates.isEmpty()) {
        return List.of(new AuthorizationGateResult("PROVIDER", false, fallbackReason));
      }
      return List.copyOf(gates);
    } catch (RuntimeException exception) {
      return List.of(new AuthorizationGateResult("PROVIDER", false, fallbackReason));
    }
  }

  private static String decisiveCondition(AuthorizationDecision decision) {
    if (!decision.safeReasonCodes().isEmpty()) {
      return decision.safeReasonCodes().stream().sorted().collect(Collectors.joining(","));
    }
    return "ALL_STRUCTURAL_ENTITLEMENT_ASSURANCE_AND_KEYS_ALLOWED";
  }
}
