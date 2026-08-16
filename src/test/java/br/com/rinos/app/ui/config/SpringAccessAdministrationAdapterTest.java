package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.module.access.dto.AccessGroupSaveRequest;
import br.com.rinos.app.api.module.access.facade.AccessAdministrationFacade;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationMutationOutcome;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationSnapshot;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationCapabilities;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;
import br.com.rinos.app.api.module.access.keys.AccessControlAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;

class SpringAccessAdministrationAdapterTest {

  @AfterEach
  void cleanSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void saveGroup_shouldAuthorizeExactUiContextBeforeSendingDerivedActor() {
    SpringAuthorizationAdapter authorization = mock(SpringAuthorizationAdapter.class);
    WorkspaceAuthorizationContextAdapter workspaces = mock(WorkspaceAuthorizationContextAdapter.class);
    AccessAdministrationFacade administration = mock(AccessAdministrationFacade.class);
    UI ui = new UI();
    AuthorizationWorkspaceContext workspace = AuthorizationWorkspaceContext.tenant(31L, 41L);
    when(workspaces.require(ui)).thenReturn(workspace);
    when(administration.saveGroup(any())).thenReturn(
        new AccessAdministrationMutationOutcome(3L, 10L, true));
    var principal = new RFWAuthenticatedPrincipalAdapter(
        new RinosUserPrincipalVO(17L, "administrator@example.com"), "session-reference");
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    SpringAccessAdministrationAdapter adapter = new SpringAccessAdministrationAdapter(
        authorization, workspaces, administration);

    adapter.saveGroup(ui, 9L, 3L, "Financeiro", "Equipe", "ajuste");

    var ordered = inOrder(authorization, administration);
    ordered.verify(authorization).require(
        org.mockito.ArgumentMatchers.eq(workspace), any());
    ArgumentCaptor<AccessGroupSaveRequest> request =
        ArgumentCaptor.forClass(AccessGroupSaveRequest.class);
    ordered.verify(administration).saveGroup(request.capture());
    assertThat(request.getValue().context().tenantId()).isEqualTo(31L);
    assertThat(request.getValue().expectedRevision()).isEqualTo(9L);
    assertThat(request.getValue().actorUserId()).isEqualTo(17L);
    assertThat(request.getValue().correlationId()).startsWith("ui-access-");
  }

  @Test
  void inspect_shouldExposeOnlyIndividuallyAuthorizedSections() {
    SpringAuthorizationAdapter authorization = mock(SpringAuthorizationAdapter.class);
    WorkspaceAuthorizationContextAdapter workspaces = mock(WorkspaceAuthorizationContextAdapter.class);
    AccessAdministrationFacade administration = mock(AccessAdministrationFacade.class);
    UI ui = new UI();
    AuthorizationWorkspaceContext workspace = AuthorizationWorkspaceContext.global();
    when(workspaces.require(ui)).thenReturn(workspace);
    AuthorizationDecision views = mock(AuthorizationDecision.class);
    AuthorizationDecision management = mock(AuthorizationDecision.class);
    AuthorizationKeyResult catalog = mock(AuthorizationKeyResult.class);
    when(catalog.key()).thenReturn(AccessControlAccessKeys.GLOBAL_CATALOG_VIEW);
    when(catalog.allowed()).thenReturn(true);
    when(views.keyResults()).thenReturn(List.of(catalog));
    when(views.structuralGates()).thenReturn(List.of());
    when(views.entitlementGates()).thenReturn(List.of());
    when(views.assuranceGates()).thenReturn(List.of());
    when(management.keyResults()).thenReturn(List.of());
    when(management.structuralGates()).thenReturn(List.of());
    when(management.entitlementGates()).thenReturn(List.of());
    when(management.assuranceGates()).thenReturn(List.of());
    when(authorization.decide(org.mockito.ArgumentMatchers.eq(workspace), any()))
        .thenAnswer(invocation -> {
          AuthorizationOperation operation = invocation.getArgument(1);
          return operation.code().contains("manage") ? management : views;
        });
    AccessAdministrationSnapshot snapshot = mock(AccessAdministrationSnapshot.class);
    when(administration.inspect(any(), any())).thenReturn(snapshot);
    SpringAccessAdministrationAdapter adapter = new SpringAccessAdministrationAdapter(
        authorization, workspaces, administration);

    assertThat(adapter.inspect(ui)).isSameAs(snapshot);

    ArgumentCaptor<AccessAdministrationCapabilities> capabilities =
        ArgumentCaptor.forClass(AccessAdministrationCapabilities.class);
    org.mockito.Mockito.verify(administration).inspect(
        org.mockito.ArgumentMatchers.eq(workspace.context()), capabilities.capture());
    assertThat(capabilities.getValue().catalogView()).isTrue();
    assertThat(capabilities.getValue().groupView()).isFalse();
    assertThat(capabilities.getValue().ruleView()).isFalse();
  }
}
