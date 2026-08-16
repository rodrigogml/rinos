package br.com.rinos.app.backend.module.access.service;

import java.util.Set;

import br.com.rinos.app.api.module.plans.vo.EntitlementRequirement;

/** Resultado minimizado da consulta de direitos de plano. */
public record PlanEntitlementAccessSnapshot(
    boolean sourceAvailable,
    Set<EntitlementRequirement> unavailableRequirements) {

  public PlanEntitlementAccessSnapshot {
    unavailableRequirements = unavailableRequirements == null
        ? Set.of() : Set.copyOf(unavailableRequirements);
    if (!sourceAvailable && !unavailableRequirements.isEmpty()) {
      throw new IllegalArgumentException("unavailable plan source cannot report entitlements");
    }
  }

  public static PlanEntitlementAccessSnapshot unavailable() {
    return new PlanEntitlementAccessSnapshot(false, Set.of());
  }

  public static PlanEntitlementAccessSnapshot available(
      Set<EntitlementRequirement> unavailableRequirements) {
    return new PlanEntitlementAccessSnapshot(true, unavailableRequirements);
  }
}
