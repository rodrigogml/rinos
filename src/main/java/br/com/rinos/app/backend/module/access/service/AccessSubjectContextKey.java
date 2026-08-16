package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Chave local mínima de um snapshot humano, sem estado de sessão. */
public record AccessSubjectContextKey(AccessScope scope, Long tenantId, long subjectId) {

  public AccessSubjectContextKey {
    if (scope == null || subjectId <= 0
        || scope == AccessScope.GLOBAL && tenantId != null
        || scope == AccessScope.TENANT && (tenantId == null || tenantId <= 0)) {
      throw new IllegalArgumentException("access subject context key is inconsistent");
    }
  }
}
