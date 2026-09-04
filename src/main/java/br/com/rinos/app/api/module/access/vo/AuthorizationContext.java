package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Contexto global ou de tenant explicitamente identificado. */
public record AuthorizationContext(
    AccessScope scope,
    Long tenantId,
    Long contextRevision) implements java.io.Serializable {

  public AuthorizationContext {
    scope = Objects.requireNonNull(scope, "scope must not be null");
    if (scope == AccessScope.GLOBAL && tenantId != null) {
      throw new IllegalArgumentException("global context must not have tenantId");
    }
    if (scope == AccessScope.TENANT && (tenantId == null || tenantId <= 0)) {
      throw new IllegalArgumentException("tenant context requires a positive tenantId");
    }
    if (contextRevision != null && contextRevision < 0) {
      throw new IllegalArgumentException("contextRevision must not be negative");
    }
  }

  public static AuthorizationContext global() {
    return new AuthorizationContext(AccessScope.GLOBAL, null, null);
  }

  public static AuthorizationContext tenant(long tenantId) {
    return new AuthorizationContext(AccessScope.TENANT, tenantId, null);
  }

  public AuthorizationContext withRevision(long revision) {
    return new AuthorizationContext(scope, tenantId, revision);
  }
}
