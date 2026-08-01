package br.com.rinos.app.ui.module.identity.view;

import java.time.Instant;

import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.authentication.vo.RFWExternalRegistrationChallengeVO;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;

/**
 * Expõe somente em testes a continuação externa controlada para validação da interface.
 *
 * <p>A rota não integra o artefato de produção e evita publicar uma entrada capaz de fabricar
 * challenges de cadastro externo.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-01
 */
@Route("test/external-registration")
@PageTitle("Rinos")
@AnonymousAllowed
public class ExternalRegistrationTestView extends Main {

  /**
   * Compõe o renderer real do RFW com documentos e challenge determinísticos do harness.
   *
   * @param componentFactory composição real da interface do Rinos
   */
  public ExternalRegistrationTestView(RinosAccessComponentFactory componentFactory) {
    RFWAccessComponent accessComponent = componentFactory.create(
        getTranslation("registration.legal-documents-unavailable"));
    accessComponent.openExternalRegistration(new RFWExternalRegistrationChallengeVO(
        "test-only-external-registration",
        "google",
        "verified@example.com",
        Instant.parse("2026-08-01T15:00:00Z")));
    add(accessComponent);
    setSizeFull();
    getStyle()
        .set("display", "flex")
        .set("align-items", "center")
        .set("justify-content", "center");
  }
}
