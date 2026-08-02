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

@DisplayName("Rota pública da confirmação de cancelamento")
class RegistrationCancellationViewTest {

  @Test
  void resolveEntry_shouldOpenConfirmationWithOpaqueProof_withoutIdentifier() {
    RFWAccessEntryRequestVO entry = RegistrationCancellationView.resolveEntry(
        QueryParameters.simple(Map.of("token", "opaque-proof")));

    assertThat(entry.step()).isEqualTo(
        RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
    assertThat(entry.identifier()).isNull();
    assertThat(entry.proof()).isEqualTo("opaque-proof");
  }

  @Test
  void resolveEntry_shouldKeepManualConfirmation_whenProofIsAbsentOrRepeated() {
    RFWAccessEntryRequestVO absent = RegistrationCancellationView.resolveEntry(
        QueryParameters.empty());
    RFWAccessEntryRequestVO repeated = RegistrationCancellationView.resolveEntry(
        QueryParameters.full(Map.of("token", new String[] {"first", "second"})));

    assertThat(absent.step()).isEqualTo(
        RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
    assertThat(absent.proof()).isNull();
    assertThat(repeated.step()).isEqualTo(
        RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
    assertThat(repeated.proof()).isNull();
  }

  @Test
  void resolveEntry_shouldDiscardExcessivelyLargeProof() {
    RFWAccessEntryRequestVO entry = RegistrationCancellationView.resolveEntry(
        QueryParameters.simple(Map.of("token", "x".repeat(513))));

    assertThat(entry.proof()).isNull();
  }

  @Test
  void beforeEnter_shouldConsumeProofAndReplaceSensitiveBrowserLocation() {
    RinosAccessComponentFactory factory = mock(RinosAccessComponentFactory.class);
    RFWAccessComponent access = mock(RFWAccessComponent.class);
    when(access.getElement()).thenReturn(new Element("div"));
    when(factory.create(anyString())).thenReturn(access);
    RegistrationCancellationView view = new RegistrationCancellationView(factory);
    BeforeEnterEvent event = mock(BeforeEnterEvent.class);
    UI ui = mock(UI.class);
    Page page = mock(Page.class);
    History history = mock(History.class);
    when(event.getLocation()).thenReturn(new Location(
        "cancel-registration",
        QueryParameters.simple(Map.of("token", "opaque-proof"))));
    when(event.getUI()).thenReturn(ui);
    when(ui.getPage()).thenReturn(page);
    when(page.getHistory()).thenReturn(history);

    view.beforeEnter(event);

    verify(access).open(new RFWAccessEntryRequestVO(
        RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION,
        null,
        "opaque-proof"));
    ArgumentCaptor<Location> location = ArgumentCaptor.forClass(Location.class);
    verify(history).replaceState(isNull(), location.capture());
    assertThat(location.getValue().getPath()).isEqualTo("cancel-registration");
    assertThat(location.getValue().getQueryParameters().getParameters()).isEmpty();
  }

  @Test
  void route_shouldBeAnonymousAndCanonical() {
    Route route = RegistrationCancellationView.class.getAnnotation(Route.class);

    assertThat(route).isNotNull();
    assertThat(route.value()).isEqualTo("cancel-registration");
    assertThat(RegistrationCancellationView.class.isAnnotationPresent(AnonymousAllowed.class))
        .isTrue();
  }
}
