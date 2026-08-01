package br.com.rinos.app.ui.module.identity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.History;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessEntryRequestVO;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;

@DisplayName("Rota pública de acesso")
class LoginViewTest {

  /**
   * A view hospedeira apenas centraliza o componente produzido pela composição do RFW.
   */
  @Test
  void constructor_shouldAddFactoryComponent_withoutCreatingParallelForm() {
    RinosAccessComponentFactory factory = mock(RinosAccessComponentFactory.class);
    RFWAccessComponent access = mock(RFWAccessComponent.class);
    when(access.getElement()).thenReturn(new Element("div"));
    when(factory.create(anyString())).thenReturn(access);

    LoginView view = new LoginView(factory);

    assertThat(view.getElement().getChildCount()).isEqualTo(1);
    verify(factory).create(anyString());
  }

  @Test
  void resolveEntry_shouldOpenActivationWithOpaqueProof_withoutIdentifier() {
    RFWAccessEntryRequestVO entry = LoginView.resolveEntry(QueryParameters.simple(Map.of(
        "step", "activation",
        "proof", "opaque-proof")));

    assertThat(entry.step()).isEqualTo(RFWAccessStepEnum.ACTIVATION);
    assertThat(entry.identifier()).isNull();
    assertThat(entry.proof()).isEqualTo("opaque-proof");
  }

  @Test
  void resolveEntry_shouldOpenManualActivation_whenProofIsAbsent() {
    RFWAccessEntryRequestVO entry = LoginView.resolveEntry(QueryParameters.simple(Map.of(
        "step", "activation")));

    assertThat(entry.step()).isEqualTo(RFWAccessStepEnum.ACTIVATION);
    assertThat(entry.proof()).isNull();
  }

  @Test
  void resolveEntry_shouldFallBackToSignIn_forUnknownOrRepeatedIntention() {
    RFWAccessEntryRequestVO unknown = LoginView.resolveEntry(QueryParameters.simple(Map.of(
        "step", "password-reset",
        "proof", "opaque-proof")));
    RFWAccessEntryRequestVO repeated = LoginView.resolveEntry(QueryParameters.full(Map.of(
        "step", new String[] {"activation", "activation"},
        "proof", new String[] {"opaque-proof"})));

    assertThat(unknown.step()).isEqualTo(RFWAccessStepEnum.SIGN_IN);
    assertThat(repeated.step()).isEqualTo(RFWAccessStepEnum.SIGN_IN);
  }

  @Test
  void resolveEntry_shouldFallBackToSignIn_forRepeatedProof() {
    RFWAccessEntryRequestVO entry = LoginView.resolveEntry(QueryParameters.full(Map.of(
        "step", new String[] {"activation"},
        "proof", new String[] {"first-proof", "second-proof"})));

    assertThat(entry.step()).isEqualTo(RFWAccessStepEnum.SIGN_IN);
  }

  @Test
  void beforeEnter_shouldConsumeProofAndReplaceSensitiveBrowserLocation() {
    RinosAccessComponentFactory factory = mock(RinosAccessComponentFactory.class);
    RFWAccessComponent access = mock(RFWAccessComponent.class);
    when(access.getElement()).thenReturn(new Element("div"));
    when(factory.create(anyString())).thenReturn(access);
    LoginView view = new LoginView(factory);
    BeforeEnterEvent event = mock(BeforeEnterEvent.class);
    UI ui = mock(UI.class);
    Page page = mock(Page.class);
    History history = mock(History.class);
    when(event.getLocation()).thenReturn(new Location(
        "login",
        QueryParameters.simple(Map.of(
            "step", "activation",
            "proof", "opaque-proof"))));
    when(event.getUI()).thenReturn(ui);
    when(ui.getPage()).thenReturn(page);
    when(page.getHistory()).thenReturn(history);

    view.beforeEnter(event);

    verify(access).open(new RFWAccessEntryRequestVO(
        RFWAccessStepEnum.ACTIVATION,
        null,
        "opaque-proof"));
    ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
    verify(history).replaceState(
        isNull(),
        location.capture());
    assertThat(location.getValue().getPath()).isEqualTo("login");
    assertThat(location.getValue().getQueryParameters().getParameters()).isEmpty();
  }

  /**
   * A rota canônica permanece anônima e estável conforme a especificação.
   */
  @Test
  void route_shouldBeAnonymousAndCanonical() {
    Route route = LoginView.class.getAnnotation(Route.class);

    assertThat(route).isNotNull();
    assertThat(route.value()).isEqualTo("login");
    assertThat(LoginView.class.isAnnotationPresent(AnonymousAllowed.class)).isTrue();
  }
}
