package br.com.rinos.app.backend.module.access.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AccessExplanationRequest;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceStatus;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.api.module.access.vo.AuthorizationRuleSource;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;
import br.com.rinos.app.backend.module.access.service.AccessDecisionAuditService;
import br.com.rinos.app.backend.module.access.service.AccessRuleResolutionService;
import br.com.rinos.app.backend.module.access.service.AuthorizationAssuranceGateProvider;
import br.com.rinos.app.backend.module.access.service.AuthorizationEntitlementGateProvider;
import br.com.rinos.app.backend.module.access.service.AuthorizationStructuralGateProvider;
import br.com.rinos.app.backend.module.access.service.ResolvedAccessSnapshot;
import br.com.rinos.app.backend.module.access.service.SystemOperationAuthorizer;

class AuthorizationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final AccessKeyDescriptor KEY = AccessControlAccessKeys.GLOBAL_RULE_VIEW;

  @Test
  void decide_shouldAuthorizeOnlyWhenAllKeyAndExternalGatesAllow() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("IDENTITY", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("PLAN", true, null)));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    when(revision.current(AccessScope.GLOBAL, null)).thenReturn(3L);
    AuthorizationRuleSource source = new AuthorizationRuleSource(
        AuthorizationSourceType.DIRECT, "rule:1", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, null, null);
    AuthorizationKeyResult keyResult = new AuthorizationKeyResult(
        KEY, true, List.of(source), List.of(), false, List.of());
    when(rules.resolveHuman(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ResolvedAccessSnapshot(3L, List.of(keyResult), null));

    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));
    var decision = facade.decide(request(Set.of(KEY)));

    assertThat(decision.allowed()).isTrue();
    assertThat(decision.safeReasonCodes()).isEmpty();
    assertThat(decision.revision()).isEqualTo(3L);
  }

  @Test
  void decide_shouldDenyCompositeOperationWhenOneKeyIsMissing() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("IDENTITY", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("PLAN", true, null)));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    when(revision.current(AccessScope.GLOBAL, null)).thenReturn(3L);
    AuthorizationKeyDescriptorPair pair = new AuthorizationKeyDescriptorPair(
        KEY, AccessControlAccessKeys.GLOBAL_EXPLAIN);
    AuthorizationRuleSource source = new AuthorizationRuleSource(
        AuthorizationSourceType.DIRECT, "rule:1", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, null, null);
    when(rules.resolveHuman(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ResolvedAccessSnapshot(3L, List.of(
            new AuthorizationKeyResult(pair.first(), true, List.of(source), List.of(), false, List.of()),
            new AuthorizationKeyResult(pair.second(), false, List.of(), List.of(), true, List.of())),
            null));

    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));
    var decision = facade.decide(request(Set.of(pair.first(), pair.second())));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.safeReasonCodes()).contains("ACL_KEY_MISSING");
    assertThat(decision.keyResults()).hasSize(2);
  }

  @Test
  void decide_shouldFailSafeWhenExternalProviderThrows() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("IDENTITY", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("plan service offline"));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    when(revision.current(AccessScope.GLOBAL, null)).thenReturn(3L);
    AuthorizationRuleSource source = new AuthorizationRuleSource(
        AuthorizationSourceType.DIRECT, "rule:1", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, null, null);
    when(rules.resolveHuman(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ResolvedAccessSnapshot(3L, List.of(
            new AuthorizationKeyResult(KEY, true, List.of(source), List.of(), false, List.of())),
            null));
    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));

    var decision = facade.decide(request(Set.of(KEY)));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.safeReasonCodes()).contains("ACL_PLAN_UNAVAILABLE");
  }

  @Test
  void decide_shouldResolveRegisteredSystemSourceWithoutHumanRules() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    SystemOperationAuthorizer system = mock(SystemOperationAuthorizer.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("SYSTEM_ORIGIN", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("PLAN", true, null)));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    AuthorizationRuleSource source = new AuthorizationRuleSource(
        AuthorizationSourceType.SYSTEM_SOURCE, "system:test", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, null, null);
    when(system.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ResolvedAccessSnapshot(9L, List.of(
            new AuthorizationKeyResult(KEY, true, List.of(source), List.of(), false, List.of())), null));
    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, system,
        mock(AccessDecisionAuditService.class));
    AuthorizationRequest request = new AuthorizationRequest(
        AuthorizationActor.system("test"), null, AuthorizationContext.global(),
        "access.system.test", Set.of(KEY), null, true, AuthorizationExplanationMode.NONE);

    assertThat(facade.decide(request).allowed()).isTrue();
    org.mockito.Mockito.verifyNoInteractions(rules);
  }

  @Test
  void explain_shouldAuthorizeRequesterBeforeReturningTargetSourcesAndDistinctGateReason() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("IDENTITY", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
      AuthorizationRequest evaluated = invocation.getArgument(0);
      return List.of(new AuthorizationGateResult("PLAN", !evaluated.operationCode().equals("target.operation"),
          evaluated.operationCode().equals("target.operation") ? "ACL_PLAN_UNAVAILABLE" : null));
    });
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    when(revision.current(AccessScope.GLOBAL, null)).thenReturn(3L);
    AuthorizationRuleSource explainPermit = new AuthorizationRuleSource(
        AuthorizationSourceType.DIRECT, "rule:explain", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, null, null);
    AuthorizationRuleSource targetPermit = new AuthorizationRuleSource(
        AuthorizationSourceType.GROUP, "rule:permit", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, NOW.minusSeconds(60), NOW.plusSeconds(60));
    AuthorizationRuleSource targetBlock = new AuthorizationRuleSource(
        AuthorizationSourceType.DIRECT, "rule:block", AccessRuleEffect.BLOQUEAR,
        AuthorizationSourceStatus.CURRENT, null, null);
    when(rules.resolveHuman(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> {
          AuthorizationRequest evaluated = invocation.getArgument(0);
          if (evaluated.operationCode().equals("access.explanation.view")) {
            return new ResolvedAccessSnapshot(3L, List.of(new AuthorizationKeyResult(
                AccessControlAccessKeys.GLOBAL_EXPLAIN, true, List.of(explainPermit),
                List.of(), false, List.of())), null);
          }
          return new ResolvedAccessSnapshot(3L, List.of(new AuthorizationKeyResult(
              KEY, false, List.of(targetPermit), List.of(targetBlock), false, List.of())), null);
        });
    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));
    AuthorizationRequest target = new AuthorizationRequest(
        AuthorizationActor.human(22L), null, AuthorizationContext.global(), "target.operation",
        Set.of(KEY), assurance(), false, AuthorizationExplanationMode.ADMINISTRATIVE);

    var explanation = facade.explain(new AccessExplanationRequest(
        AuthorizationActor.human(11L), null, assurance(), target));

    assertThat(explanation.decision().allowed()).isFalse();
    assertThat(explanation.decision().safeReasonCodes())
        .contains("ACL_KEY_BLOCKED", "ACL_PLAN_UNAVAILABLE");
    assertThat(explanation.decision().keyResults().getFirst().permitSources())
        .containsExactly(targetPermit);
    assertThat(explanation.decision().keyResults().getFirst().blockingSources())
        .containsExactly(targetBlock);
    assertThat(explanation.decisiveCondition())
        .contains("ACL_KEY_BLOCKED", "ACL_PLAN_UNAVAILABLE");
  }

  @Test
  void explain_shouldDenyWithoutResolvingTargetWhenRequesterCannotKnowTenant() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("MEMBERSHIP", false, "ACL_INVALID_CONTEXT")));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("PLAN", true, null)));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    when(revision.current(AccessScope.TENANT, 77L)).thenReturn(5L);
    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));
    AuthorizationRequest target = new AuthorizationRequest(
        AuthorizationActor.human(22L), 220L, AuthorizationContext.tenant(77L), "target.operation",
        Set.of(AccessControlAccessKeys.TENANT_RULE_VIEW), assurance(), false,
        AuthorizationExplanationMode.ADMINISTRATIVE);

    assertThatThrownBy(() -> facade.explain(new AccessExplanationRequest(
        AuthorizationActor.human(11L), 110L, assurance(), target)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ACL_EXPLANATION_FORBIDDEN");
    verify(rules, never()).resolveHuman(
        org.mockito.ArgumentMatchers.eq(target), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void explain_shouldRequireAdministrativeModeEvenForAuthorizedRequester() {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("IDENTITY", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("PLAN", true, null)));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("ASSURANCE", true, null)));
    AuthorizationRuleSource source = new AuthorizationRuleSource(
        AuthorizationSourceType.DIRECT, "rule:explain", AccessRuleEffect.PERMITIR,
        AuthorizationSourceStatus.CURRENT, null, null);
    when(rules.resolveHuman(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ResolvedAccessSnapshot(3L, List.of(new AuthorizationKeyResult(
            AccessControlAccessKeys.GLOBAL_EXPLAIN, true, List.of(source), List.of(), false,
            List.of())), null));
    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));
    AuthorizationRequest target = new AuthorizationRequest(
        AuthorizationActor.human(22L), null, AuthorizationContext.global(), "target.operation",
        Set.of(KEY), assurance(), false, AuthorizationExplanationMode.SAFE);

    assertThatThrownBy(() -> facade.explain(new AccessExplanationRequest(
        AuthorizationActor.human(11L), null, assurance(), target)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ACL_EXPLANATION_FORBIDDEN");
  }

  @Test
  void explain_shouldDenyWhenRequesterPermissionIsMissing() {
    assertExplanationVisibilityDenied(false, true);
  }

  @Test
  void explain_shouldDenyWhenRequesterAssuranceIsInsufficient() {
    assertExplanationVisibilityDenied(true, false);
  }

  private static AuthorizationRequest request(Set<AccessKeyDescriptor> keys) {
    return new AuthorizationRequest(
        AuthorizationActor.human(11L), null, AuthorizationContext.global(), "access.test", keys,
        new AuthenticationAssurance(
            AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
            NOW.minusSeconds(60), NOW.minusSeconds(60)), false,
        AuthorizationExplanationMode.SAFE);
  }

  private static AuthenticationAssurance assurance() {
    return new AuthenticationAssurance(
        AuthenticationAssuranceEnum.MULTI_FACTOR, Set.of(AuthenticationMethodEnum.PASSKEY),
        NOW.minusSeconds(60), NOW.minusSeconds(60));
  }

  private static void assertExplanationVisibilityDenied(
      boolean hasExplainPermit, boolean assuranceAllowed) {
    AuthorizationStructuralGateProvider structural = mock(AuthorizationStructuralGateProvider.class);
    AuthorizationEntitlementGateProvider entitlement = mock(AuthorizationEntitlementGateProvider.class);
    AuthorizationAssuranceGateProvider assurance = mock(AuthorizationAssuranceGateProvider.class);
    AccessRuleResolutionService rules = mock(AccessRuleResolutionService.class);
    AccessContextRevisionService revision = mock(AccessContextRevisionService.class);
    when(structural.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("IDENTITY", true, null)));
    when(entitlement.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(
        List.of(new AuthorizationGateResult("PLAN", true, null)));
    when(assurance.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
        new AuthorizationGateResult("ASSURANCE", assuranceAllowed,
            assuranceAllowed ? null : "ACL_ASSURANCE_REQUIRED")));
    when(revision.current(AccessScope.GLOBAL, null)).thenReturn(3L);
    List<AuthorizationRuleSource> permits = hasExplainPermit
        ? List.of(new AuthorizationRuleSource(
            AuthorizationSourceType.DIRECT, "rule:explain", AccessRuleEffect.PERMITIR,
            AuthorizationSourceStatus.CURRENT, null, null))
        : List.of();
    when(rules.resolveHuman(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ResolvedAccessSnapshot(3L, List.of(new AuthorizationKeyResult(
            AccessControlAccessKeys.GLOBAL_EXPLAIN, hasExplainPermit, permits, List.of(),
            !hasExplainPermit, List.of())), null));
    AuthorizationFacadeImpl facade = new AuthorizationFacadeImpl(
        revision, rules, structural, entitlement, assurance, mock(SystemOperationAuthorizer.class),
        mock(AccessDecisionAuditService.class));
    AuthorizationRequest target = new AuthorizationRequest(
        AuthorizationActor.human(22L), null, AuthorizationContext.global(), "target.operation",
        Set.of(KEY), assurance(), false, AuthorizationExplanationMode.ADMINISTRATIVE);

    assertThatThrownBy(() -> facade.explain(new AccessExplanationRequest(
        AuthorizationActor.human(11L), null, assurance(), target)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ACL_EXPLANATION_FORBIDDEN");
    verify(rules, times(1)).resolveHuman(
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  private record AuthorizationKeyDescriptorPair(
      AccessKeyDescriptor first, AccessKeyDescriptor second) {
  }
}
