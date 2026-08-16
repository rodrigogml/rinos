package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;

class WorkspaceAuthorizationContextAdapterTest {

  @Test
  void twoUiInstances_shouldKeepIndependentTenantAndMembershipReferences() {
    WorkspaceAuthorizationContextAdapter adapter = new WorkspaceAuthorizationContextAdapter();
    UI firstWindow = new UI();
    UI secondWindow = new UI();

    adapter.select(firstWindow, AuthorizationWorkspaceContext.tenant(10L, 100L));
    adapter.select(secondWindow, AuthorizationWorkspaceContext.tenant(20L, 200L));

    assertThat(adapter.require(firstWindow).context().tenantId()).isEqualTo(10L);
    assertThat(adapter.require(firstWindow).membershipId()).isEqualTo(100L);
    assertThat(adapter.require(secondWindow).context().tenantId()).isEqualTo(20L);
    assertThat(adapter.require(secondWindow).membershipId()).isEqualTo(200L);
    adapter.clear(firstWindow);
    assertThatThrownBy(() -> adapter.require(firstWindow))
        .hasMessage("ACL_WORKSPACE_CONTEXT_REQUIRED");
    assertThat(adapter.require(secondWindow).context().tenantId()).isEqualTo(20L);
  }
}
