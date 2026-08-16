package br.com.rinos.app.ui.config;

import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.dto.AccessExplanationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.facade.AuthorizationAuthenticationFacade;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AccessExplanation;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;
import br.com.rinos.app.api.module.access.vo.HumanAuthorizationContext;

/** Deriva a requisição humana do Spring e de um contexto explícito, sem authorities ACL. */
@Component
@org.springframework.context.annotation.Lazy
public class SpringAuthorizationAdapter {

  private final AuthorizationFacade authorization;
  private final AuthorizationAuthenticationFacade authenticationSessions;
  private final WorkspaceAuthorizationContextAdapter workspaces;
  private final Clock clock;

  @Autowired
  public SpringAuthorizationAdapter(
      AuthorizationFacade authorization,
      AuthorizationAuthenticationFacade authenticationSessions,
      WorkspaceAuthorizationContextAdapter workspaces) {
    this(authorization, authenticationSessions, workspaces, Clock.systemUTC());
  }

  SpringAuthorizationAdapter(
      AuthorizationFacade authorization,
      AuthorizationAuthenticationFacade authenticationSessions,
      WorkspaceAuthorizationContextAdapter workspaces,
      Clock clock) {
    this.authorization = authorization;
    this.authenticationSessions = authenticationSessions;
    this.workspaces = workspaces;
    this.clock = clock;
  }

  /** Autoriza uma entrada Vaadin usando o tenant da UI exata. */
  public AuthorizationDecision require(UI ui, AuthorizationOperation operation) {
    return require(workspaces.require(ui), operation);
  }

  /** Autoriza uma entrada Spring interna cujo contexto foi recebido explicitamente. */
  public AuthorizationDecision require(
      AuthorizationWorkspaceContext workspace, AuthorizationOperation operation) {
    return authorization.require(request(workspace, operation));
  }

  /** Decide sem lançar negação, preservando a mesma derivação autenticada de {@link #require}. */
  public AuthorizationDecision decide(
      AuthorizationWorkspaceContext workspace, AuthorizationOperation operation) {
    return authorization.decide(request(workspace, operation));
  }

  /** Publica a fotografia autenticada minima para facades consumidoras, sem authorities ACL. */
  public HumanAuthorizationContext current(AuthorizationWorkspaceContext workspace) {
    if (workspace == null) throw new IllegalArgumentException("workspace must not be null");
    AuthenticatedSession authenticated = authenticatedSession();
    return new HumanAuthorizationContext(
        AuthorizationActor.human(authenticated.principal().user().userId()),
        workspace.membershipId(), workspace.context(), authenticated.assurance());
  }

  /** Explica uma operacao hipotetica para um sujeito do mesmo contexto autorizado. */
  public AccessExplanation explain(
      AuthorizationWorkspaceContext workspace,
      long targetIdentityId,
      Long targetMembershipId,
      AuthorizationOperation operation) {
    if (workspace == null || operation == null || targetIdentityId <= 0) {
      throw new IllegalArgumentException("explanation target is incomplete");
    }
    AuthenticatedSession requester = authenticatedSession();
    AuthorizationRequest target = new AuthorizationRequest(
        AuthorizationActor.human(targetIdentityId), targetMembershipId, workspace.context(),
        operation.code(), operation.requiredKeys(), requester.assurance(), operation.sensitive(),
        AuthorizationExplanationMode.ADMINISTRATIVE);
    return authorization.explain(new AccessExplanationRequest(
        AuthorizationActor.human(requester.principal().user().userId()), workspace.membershipId(),
        requester.assurance(), target));
  }

  private AuthorizationRequest request(
      AuthorizationWorkspaceContext workspace, AuthorizationOperation operation) {
    if (workspace == null || operation == null) {
      throw new IllegalArgumentException("workspace and operation must not be null");
    }
    AuthenticatedSession authenticated = authenticatedSession();
    RFWAuthenticatedPrincipalAdapter principal = authenticated.principal();
    AuthenticationAssurance assurance = authenticated.assurance();
    return new AuthorizationRequest(
        AuthorizationActor.human(principal.user().userId()), workspace.membershipId(),
        workspace.context(), operation.code(), operation.requiredKeys(), assurance,
        operation.sensitive(), AuthorizationExplanationMode.NONE);
  }

  private AuthenticatedSession authenticatedSession() {
    Authentication current = SecurityContextHolder.getContext().getAuthentication();
    if (current == null || !current.isAuthenticated()
        || !(current.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal)) {
      throw new IllegalStateException("ACL_INVALID_AUTHENTICATION");
    }
    try {
      AuthenticationAssurance assurance = authenticationSessions.resolve(
          principal.user().userId(), principal.sessionReference(), clock.instant())
          .orElseThrow(() -> new IllegalStateException("ACL_INVALID_AUTHENTICATION"));
      return new AuthenticatedSession(principal, assurance);
    } catch (RuntimeException unavailable) {
      throw new IllegalStateException("ACL_INVALID_AUTHENTICATION");
    }
  }

  private record AuthenticatedSession(
      RFWAuthenticatedPrincipalAdapter principal, AuthenticationAssurance assurance) {
  }
}
