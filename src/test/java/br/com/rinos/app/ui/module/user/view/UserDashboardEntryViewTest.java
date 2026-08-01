package br.com.rinos.app.ui.module.user.view;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.security.PermitAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@DisplayName("Entrada reservada do Painel de Usuário")
class UserDashboardEntryViewTest {

  /**
   * Comprova que o destino pós-autenticação é estável e nunca se torna uma rota pública.
   */
  @Test
  void route_shouldRequireAuthenticatedUser_whenDashboardContentIsDeferred() {
    Route route = UserDashboardEntryView.class.getAnnotation(Route.class);

    assertThat(route).isNotNull();
    assertThat(route.value()).isEqualTo(UserDashboardEntryView.ROUTE);
    assertThat(UserDashboardEntryView.class.isAnnotationPresent(PermitAll.class)).isTrue();
    assertThat(UserDashboardEntryView.class.isAnnotationPresent(AnonymousAllowed.class)).isFalse();
  }
}
