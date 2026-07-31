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
import br.eng.rodrigogml.rfw.platform.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.platform.ui.access.RFWAccessEntryRequestVO;
import br.eng.rodrigogml.rfw.platform.ui.access.RFWAccessStepEnum;

/**
 * Hospeda o fluxo público de acesso sem duplicar componentes ou estados fornecidos pelo RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Route("login")
@PageTitle("Rinos")
@AnonymousAllowed
public class LoginView extends Main implements BeforeEnterObserver {

  private static final String STEP_PARAMETER = "step";
  private static final String PROOF_PARAMETER = "proof";
  private static final String ACTIVATION_STEP = "activation";
  private static final int MAXIMUM_PROOF_LENGTH = 512;

  private final RFWAccessComponent accessComponent;

  /**
   * Cria a rota com a composição vigente do cadastro.
   *
   * @param componentFactory composição do Rinos sobre a factory pública do RFW
   */
  public LoginView(RinosAccessComponentFactory componentFactory) {
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
   * Traduz somente entradas públicas explicitamente permitidas para a máquina de estados do RFW.
   *
   * <p>Parâmetros desconhecidos, repetidos ou excessivamente grandes não são propagados. O e-mail
   * e identificadores internos nunca fazem parte dessa entrada. Depois de entregar uma prova ao
   * componente, a rota a remove do histórico visível sem removê-la do estado efêmero da UI.
   *
   * @param event navegação corrente
   */
  @Override
  public void beforeEnter(BeforeEnterEvent event) {
    QueryParameters queryParameters = event.getLocation().getQueryParameters();
    accessComponent.open(resolveEntry(queryParameters));
    if (queryParameters.getParameters().containsKey(PROOF_PARAMETER)) {
      event.getUI().getPage().getHistory().replaceState(
          null,
          new Location("login"));
    }
  }

  static RFWAccessEntryRequestVO resolveEntry(QueryParameters queryParameters) {
    Map<String, List<String>> parameters = queryParameters.getParameters();
    if (!hasSingleValue(parameters, STEP_PARAMETER)
        || (parameters.containsKey(PROOF_PARAMETER)
            && !hasSingleValue(parameters, PROOF_PARAMETER))) {
      return RFWAccessEntryRequestVO.signIn();
    }
    String step = singleValue(parameters, STEP_PARAMETER);
    if (!ACTIVATION_STEP.equals(step)) {
      return RFWAccessEntryRequestVO.signIn();
    }
    String proof = singleValue(parameters, PROOF_PARAMETER);
    if (proof != null && proof.length() > MAXIMUM_PROOF_LENGTH) {
      return RFWAccessEntryRequestVO.signIn();
    }
    return new RFWAccessEntryRequestVO(
        RFWAccessStepEnum.ACTIVATION,
        null,
        blankToNull(proof));
  }

  private static String singleValue(
      Map<String, List<String>> parameters,
      String name) {
    List<String> values = parameters.get(name);
    return values == null || values.size() != 1 ? null : values.getFirst();
  }

  private static boolean hasSingleValue(
      Map<String, List<String>> parameters,
      String name) {
    List<String> values = parameters.get(name);
    return values != null && values.size() == 1;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
