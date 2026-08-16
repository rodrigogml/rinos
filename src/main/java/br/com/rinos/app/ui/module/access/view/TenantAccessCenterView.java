package br.com.rinos.app.ui.module.access.view;

import java.util.List;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;
import br.com.rinos.app.ui.config.SpringAccessAdministrationAdapter;
import br.com.rinos.app.ui.config.WorkspaceAuthorizationContextAdapter;
import jakarta.annotation.security.PermitAll;
import br.eng.rodrigogml.rfw.authentication.provider.RFWReauthenticationChallengeProvider;

/** Entrada de tenant; tenant e associação são explícitos na URL e revalidados pela autorização. */
@Route(TenantAccessCenterView.ROUTE)
@PageTitle("Controle de acesso da conta")
@PermitAll
public class TenantAccessCenterView extends Main implements BeforeEnterObserver {
  public static final String ROUTE = "account/access";

  private final WorkspaceAuthorizationContextAdapter workspaces;
  private final AccessCenterComponent center;

  public TenantAccessCenterView(
      WorkspaceAuthorizationContextAdapter workspaces,
      SpringAccessAdministrationAdapter administration,
      RFWReauthenticationChallengeProvider reauthentication) {
    this.workspaces = workspaces;
    this.center = new AccessCenterComponent(administration, reauthentication);
    add(center);
    setSizeFull();
  }

  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    try {
      long tenantId = parameter(event, "tenant");
      long membershipId = parameter(event, "membership");
      workspaces.select(event.getUI(), AuthorizationWorkspaceContext.tenant(tenantId, membershipId));
      center.load(event.getUI());
    } catch (IllegalArgumentException invalid) {
      event.rerouteToError(IllegalArgumentException.class, "ACL_WORKSPACE_CONTEXT_REQUIRED");
    }
  }

  private static long parameter(BeforeEnterEvent event, String name) {
    List<String> values = event.getLocation().getQueryParameters().getParameters().get(name);
    if (values == null || values.size() != 1) throw new IllegalArgumentException("missing context");
    long value = Long.parseLong(values.getFirst());
    if (value <= 0) throw new IllegalArgumentException("invalid context");
    return value;
  }
}
