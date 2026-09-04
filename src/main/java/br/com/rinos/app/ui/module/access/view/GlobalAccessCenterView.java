package br.com.rinos.app.ui.module.access.view;

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

/** Entrada global da central, isolada das áreas de tenant. */
@Route(GlobalAccessCenterView.ROUTE)
@PageTitle("Controle de acesso")
@PermitAll
public class GlobalAccessCenterView extends Main implements BeforeEnterObserver {
  public static final String ROUTE = "system/access";

  private final WorkspaceAuthorizationContextAdapter workspaces;
  private final AccessCenterComponent center;

  public GlobalAccessCenterView(
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
    workspaces.select(event.getUI(), AuthorizationWorkspaceContext.global());
    center.load(event.getUI());
  }
}
