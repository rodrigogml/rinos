package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Notificação opcional e idempotente que antecipa invalidação local. */
public record AuthorizationContextChanged(
    AccessScope scope,
    Long tenantId,
    long newRevision) {

  public AuthorizationContextChanged {
    if (scope == null || newRevision < 0
        || scope == AccessScope.GLOBAL && tenantId != null
        || scope == AccessScope.TENANT && (tenantId == null || tenantId <= 0)) {
      throw new IllegalArgumentException("authorization context change is inconsistent");
    }
  }
}
