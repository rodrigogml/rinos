package br.com.rinos.app.backend.module.access.service;

import java.util.Set;

import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;
import br.com.rinos.app.api.module.plans.vo.EntitlementSubject;

/** Porta publicada por plans-entitlements para consultar direitos sem transformá-los em ACL. */
public interface PlanEntitlementAccessPort {

  PlanEntitlementAccessSnapshot inspect(
      EntitlementSubject subject,
      Set<EntitlementRequirement> requirements);
}
