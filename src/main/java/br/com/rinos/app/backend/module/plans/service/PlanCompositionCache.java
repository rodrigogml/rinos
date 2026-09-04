package br.com.rinos.app.backend.module.plans.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

import br.com.rinos.app.api.module.plans.enums.EntitlementType;

/** Cache local somente de composições publicadas e, portanto, imutáveis. */
final class PlanCompositionCache {

  private final Map<Long, Map<String, PublishedEntitlement>> entries = new ConcurrentHashMap<>();

  Map<String, PublishedEntitlement> get(long planVersionId,
      LongFunction<Map<String, PublishedEntitlement>> loader) {
    return entries.computeIfAbsent(planVersionId, ignored -> Map.copyOf(loader.apply(planVersionId)));
  }

  int size() {
    return entries.size();
  }

  record PublishedEntitlement(
      EntitlementType type,
      Boolean booleanValue,
      Long quantityValue,
      String periodCode) {
  }
}
