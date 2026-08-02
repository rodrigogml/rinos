package br.com.rinos.app.ui.module.identity.view;

import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessEntryRequestVO;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;

/**
 * Hospeda a confirmação pública do cancelamento a partir de uma prova opaca.
 *
 * <p>A rota nunca recebe identificadores persistentes ou o e-mail. Provas ausentes, repetidas ou
 * excessivamente grandes são descartadas e mantêm a entrada manual disponível no componente RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@Route("cancel-registration")
@PageTitle("Rinos")
@AnonymousAllowed
public class RegistrationCancellationView extends Main implements BeforeEnterObserver {

  private static final String TOKEN_PARAMETER = "token";
  private static final int MAXIMUM_PROOF_LENGTH = 512;

  private final RFWAccessComponent accessComponent;

  /**
   * Cria a rota sobre a composição compartilhada do fluxo de acesso.
   *
   * @param componentFactory composição do Rinos sobre a factory pública do RFW
   */
  public RegistrationCancellationView(RinosAccessComponentFactory componentFactory) {
    accessComponent = componentFactory.create(getTranslation(
        "registration.legal-documents-unavailable"));
    add(accessComponent);
    setSizeFull();
    getStyle()
        .set("display", "flex")
        .set("align-items", "center")
        .set("justify-content", "center");
  }

  /**
   * Entrega a prova uma única vez ao estado efêmero e a remove do histórico visível.
   *
   * @param event navegação corrente
   */
  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters queryParameters = event.getLocation().getQueryParameters();
    accessComponent.open(resolveEntry(queryParameters));
    if (queryParameters.getParameters().containsKey(TOKEN_PARAMETER)) {
      event.getUI().getPage().getHistory().replaceState(
          null,
          new Location("cancel-registration"));
    }
  }

  static RFWAccessEntryRequestVO resolveEntry(QueryParameters queryParameters) {
    Map<String, List<String>> parameters = queryParameters.getParameters();
    List<String> values = parameters.get(TOKEN_PARAMETER);
    String proof = values != null && values.size() == 1 ? blankToNull(values.getFirst()) : null;
    if (proof != null && proof.length() > MAXIMUM_PROOF_LENGTH) {
      proof = null;
    }
    return new RFWAccessEntryRequestVO(
        RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION,
        null,
        proof);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
