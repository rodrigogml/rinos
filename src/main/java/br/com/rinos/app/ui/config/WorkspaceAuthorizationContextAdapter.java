package br.com.rinos.app.ui.config;

import org.springframework.stereotype.Component;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;

/** Mantém somente a referência de contexto na instância exata da UI Vaadin. */
@Component
public class WorkspaceAuthorizationContextAdapter {

  public void select(UI ui, AuthorizationWorkspaceContext workspace) {
    if (ui == null) {
      throw new IllegalArgumentException("ui must not be null");
    }
    if (workspace == null) {
      throw new IllegalArgumentException("workspace must not be null");
    }
    ComponentUtil.setData(ui, AuthorizationWorkspaceContext.class, workspace);
  }

  public AuthorizationWorkspaceContext require(UI ui) {
    if (ui == null) {
      throw new IllegalArgumentException("ui must not be null");
    }
    AuthorizationWorkspaceContext workspace =
        ComponentUtil.getData(ui, AuthorizationWorkspaceContext.class);
    if (workspace == null) {
      throw new IllegalStateException("ACL_WORKSPACE_CONTEXT_REQUIRED");
    }
    return workspace;
  }

  public void clear(UI ui) {
    if (ui != null) {
      ComponentUtil.setData(ui, AuthorizationWorkspaceContext.class, null);
    }
  }
}
