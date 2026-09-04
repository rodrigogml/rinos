package br.com.rinos.app.backend.module.access.component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.EntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.PersonalEntitlementSubject;
import br.com.rinos.app.api.module.plans.vo.TenantEntitlementSubject;
import br.com.rinos.app.backend.module.access.service.AuthorizationEntitlementGateProvider;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessPort;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessSnapshot;

/**
 * Avalia direitos de plano pela porta publicada pelo módulo de planos, sem convertê-los em regras ACL.
 *
 * <p>A ausência do direito e a indisponibilidade da fonte continuam distintos na decisão. Quando nenhuma fonte de
 * planos estiver disponível, o adapter de fallback nega de forma segura.</p>
 */
@Component
@org.springframework.context.annotation.Lazy
public class DefaultAuthorizationEntitlementGateProvider
    implements AuthorizationEntitlementGateProvider {

  private final PlanEntitlementAccessPort entitlementAccessPort;

  public DefaultAuthorizationEntitlementGateProvider(
      PlanEntitlementAccessPort entitlementAccessPort) {
    this.entitlementAccessPort = entitlementAccessPort;
  }

  @Override
  public List<AuthorizationGateResult> evaluate(AuthorizationRequest request) {
    Set<EntitlementRequirement> required = request.requiredKeys().stream()
        .map(key -> key.entitlementRequirement())
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
    if (required.isEmpty()) {
      return List.of(new AuthorizationGateResult("PLAN_ENTITLEMENT", true, null));
    }
    Set<ContractScope> scopes = required.stream()
        .map(EntitlementRequirement::subjectScope)
        .collect(Collectors.toUnmodifiableSet());
    if (scopes.size() != 1) {
      return List.of(new AuthorizationGateResult(
          "PLAN_ENTITLEMENT", false, "ACL_PLAN_CONTEXT_INVALID"));
    }
    EntitlementSubject subject = resolveSubject(request, scopes.iterator().next());
    if (subject == null) {
      return List.of(new AuthorizationGateResult(
          "PLAN_ENTITLEMENT", false, "ACL_PLAN_CONTEXT_INVALID"));
    }
    PlanEntitlementAccessSnapshot snapshot = entitlementAccessPort.inspect(
        subject, required);
    if (!snapshot.sourceAvailable()) {
      return List.of(new AuthorizationGateResult(
          "PLAN_ENTITLEMENT", false, "ACL_PLAN_UNAVAILABLE"));
    }
    boolean allowed = snapshot.unavailableRequirements().isEmpty();
    return List.of(new AuthorizationGateResult(
        "PLAN_ENTITLEMENT", allowed, allowed ? null : "ACL_PLAN_REQUIRED"));
  }

  private static EntitlementSubject resolveSubject(
      AuthorizationRequest request,
      ContractScope scope) {
    if (scope == ContractScope.TENANT) {
      Long tenantId = request.context().tenantId();
      return tenantId == null ? null : new TenantEntitlementSubject(tenantId);
    }
    if (request.actor().type() != AuthorizationActorType.HUMAN
        || request.actor().identityId() == null) {
      return null;
    }
    return new PersonalEntitlementSubject(request.actor().identityId());
  }
}
