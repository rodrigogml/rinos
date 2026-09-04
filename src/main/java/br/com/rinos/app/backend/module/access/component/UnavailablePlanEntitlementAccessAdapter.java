package br.com.rinos.app.backend.module.access.component;

import java.util.Set;

import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.EntitlementSubject;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessPort;
import br.com.rinos.app.backend.module.access.service.PlanEntitlementAccessSnapshot;

/** Adapter fail-safe removido automaticamente quando plans-entitlements publicar a porta real. */
public class UnavailablePlanEntitlementAccessAdapter implements PlanEntitlementAccessPort {

  @Override
  public PlanEntitlementAccessSnapshot inspect(
      EntitlementSubject subject,
      Set<EntitlementRequirement> requirements) {
    return PlanEntitlementAccessSnapshot.unavailable();
  }
}
