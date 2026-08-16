package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Referência mínima do contexto pretendido mantida pela área de trabalho exata. */
public record AuthorizationWorkspaceContext(
    AuthorizationContext context,
    Long membershipId) implements java.io.Serializable {

  public AuthorizationWorkspaceContext {
    context = Objects.requireNonNull(context, "context must not be null");
    if (context.contextRevision() != null) {
      throw new IllegalArgumentException("workspace context must not freeze a revision");
    }
    if (context.scope() == AccessScope.GLOBAL && membershipId != null) {
      throw new IllegalArgumentException("global workspace must not carry membership");
    }
    if (context.scope() == AccessScope.TENANT && (membershipId == null || membershipId <= 0)) {
      throw new IllegalArgumentException("tenant workspace requires a positive membershipId");
    }
  }

  public static AuthorizationWorkspaceContext global() {
    return new AuthorizationWorkspaceContext(AuthorizationContext.global(), null);
  }

  public static AuthorizationWorkspaceContext tenant(long tenantId, long membershipId) {
    return new AuthorizationWorkspaceContext(
        AuthorizationContext.tenant(tenantId), membershipId);
  }
}
