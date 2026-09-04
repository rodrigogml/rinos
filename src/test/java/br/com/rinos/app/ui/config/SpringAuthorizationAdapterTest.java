package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.facade.AuthorizationAuthenticationFacade;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;

class SpringAuthorizationAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void require_shouldDeriveActorAndAssuranceButKeepTenantExplicit() {
    AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    AuthorizationAuthenticationFacade sessions = mock(AuthorizationAuthenticationFacade.class);
    WorkspaceAuthorizationContextAdapter workspaces = mock(WorkspaceAuthorizationContextAdapter.class);
    AuthenticationAssurance assurance = assurance();
    RFWAuthenticatedPrincipalAdapter principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(11L, "user@example.com"),
        "8bc665d7-2ee4-4670-ab63-cfcbead17c7c");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    when(sessions.resolve(11L, principal.sessionReference(), NOW))
        .thenReturn(Optional.of(assurance));
    SpringAuthorizationAdapter adapter = new SpringAuthorizationAdapter(
        authorization, sessions, workspaces, Clock.fixed(NOW, ZoneOffset.UTC));
    AuthorizationWorkspaceContext workspace = AuthorizationWorkspaceContext.tenant(77L, 88L);
    AuthorizationOperation operation = new AuthorizationOperation(
        "tenant.access.rule.view", Set.of(AccessControlAccessKeys.TENANT_RULE_VIEW), true);

    adapter.require(workspace, operation);

    ArgumentCaptor<AuthorizationRequest> captor =
        ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(authorization).require(captor.capture());
    AuthorizationRequest request = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(request.actor().identityId()).isEqualTo(11L);
    org.assertj.core.api.Assertions.assertThat(request.context().tenantId()).isEqualTo(77L);
    org.assertj.core.api.Assertions.assertThat(request.membershipId()).isEqualTo(88L);
    org.assertj.core.api.Assertions.assertThat(request.assurance()).isSameAs(assurance);
    org.assertj.core.api.Assertions.assertThat(request.sensitive()).isTrue();
  }

  @Test
  void require_shouldFailSafelyBeforeAuthorizationWhenSessionCannotBeRevalidated() {
    AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    AuthorizationAuthenticationFacade sessions = mock(AuthorizationAuthenticationFacade.class);
    WorkspaceAuthorizationContextAdapter workspaces = mock(WorkspaceAuthorizationContextAdapter.class);
    RFWAuthenticatedPrincipalAdapter principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(11L, "user@example.com"),
        "8bc665d7-2ee4-4670-ab63-cfcbead17c7c");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    when(sessions.resolve(11L, principal.sessionReference(), NOW))
        .thenThrow(new IllegalStateException("database unavailable"));
    SpringAuthorizationAdapter adapter = new SpringAuthorizationAdapter(
        authorization, sessions, workspaces, Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> adapter.require(
        AuthorizationWorkspaceContext.global(), new AuthorizationOperation(
            "global.access.rule.view", Set.of(AccessControlAccessKeys.GLOBAL_RULE_VIEW), false)))
        .hasMessage("ACL_INVALID_AUTHENTICATION");
    verifyNoInteractions(authorization);
  }

  @Test
  void requireFromUi_shouldUseOnlyWorkspaceStoredInThatUi() {
    AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    AuthorizationAuthenticationFacade sessions = mock(AuthorizationAuthenticationFacade.class);
    WorkspaceAuthorizationContextAdapter workspaces = new WorkspaceAuthorizationContextAdapter();
    RFWAuthenticatedPrincipalAdapter principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(11L, "user@example.com"),
        "8bc665d7-2ee4-4670-ab63-cfcbead17c7c");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    when(sessions.resolve(11L, principal.sessionReference(), NOW))
        .thenReturn(Optional.of(assurance()));
    SpringAuthorizationAdapter adapter = new SpringAuthorizationAdapter(
        authorization, sessions, workspaces, Clock.fixed(NOW, ZoneOffset.UTC));
    UI ui = new UI();
    workspaces.select(ui, AuthorizationWorkspaceContext.tenant(91L, 92L));

    adapter.require(ui, new AuthorizationOperation(
        "tenant.route.open", Set.of(AccessControlAccessKeys.TENANT_RULE_VIEW), false));

    ArgumentCaptor<AuthorizationRequest> captor =
        ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(authorization).require(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().context().tenantId()).isEqualTo(91L);
    org.assertj.core.api.Assertions.assertThat(captor.getValue().membershipId()).isEqualTo(92L);
  }

  private static AuthenticationAssurance assurance() {
    return new AuthenticationAssurance(
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP),
        NOW.minusSeconds(600), NOW.minusSeconds(60));
  }
}
