package br.com.rinos.app.api.module.plans.enums;

/** Resultado seguro e explicável da avaliação de um direito. */
public enum EntitlementDecisionStatus {
  AVAILABLE,
  UNAVAILABLE,
  LIMIT_REACHED,
  INVALID_CONTEXT,
  INCONSISTENT,
  SOURCE_UNAVAILABLE
}
