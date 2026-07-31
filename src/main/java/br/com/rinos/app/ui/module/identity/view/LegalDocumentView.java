package br.com.rinos.app.ui.module.identity.view;

import java.util.Objects;
import java.util.Optional;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Main;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Section;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.eng.rodrigogml.rfw.platform.ui.content.RFWContentFormatEnum;
import br.eng.rodrigogml.rfw.platform.ui.content.RFWContentRenderer;

/**
 * Apresenta uma versão jurídica publicada usando o renderer Markdown sanitizado do RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Route("legal-document")
@PageTitle("Documento jurídico | Rinos")
@AnonymousAllowed
public class LegalDocumentView extends Main implements HasUrlParameter<String> {

  private final LegalDocumentFacade legalDocumentFacade;

  /**
   * Cria a rota pública sem consultar persistência antes de receber a referência.
   *
   * @param legalDocumentFacade contrato público do catálogo jurídico
   */
  public LegalDocumentView(LegalDocumentFacade legalDocumentFacade) {
    this.legalDocumentFacade = Objects.requireNonNull(
        legalDocumentFacade,
        "legalDocumentFacade must not be null");
    setSizeFull();
  }

  @Override
  public void setParameter(BeforeEvent event, String parameter) {
    removeAll();
    Optional<LegalDocumentContentVO> document;
    try {
      document = legalDocumentFacade.findPublishedDocument(parameter);
    } catch (RuntimeException unavailableCatalog) {
      showUnavailable();
      return;
    }
    if (document.isEmpty()) {
      showNotFound();
      return;
    }
    showDocument(document.orElseThrow());
  }

  private void showDocument(LegalDocumentContentVO document) {
    H1 title = new H1(title(document));
    Paragraph version = new Paragraph(getTranslation(
        "legal.document.version",
        document.versionName()));
    RFWContentRenderer content = new RFWContentRenderer(
        document.content(),
        RFWContentFormatEnum.MARKDOWN);
    content.setWidthFull();

    Section body = new Section(title, version, content, backToLogin());
    body.setWidthFull();
    body.getStyle()
        .set("max-width", "70rem")
        .set("margin", "0 auto")
        .set("padding", "var(--lumo-space-l)");
    add(body);
    getUI().ifPresent(ui -> ui.getPage().setTitle(title(document) + " | Rinos"));
  }

  private void showNotFound() {
    add(statusSection(
        getTranslation("legal.document.not-found.title"),
        getTranslation("legal.document.not-found.description")));
  }

  private void showUnavailable() {
    add(statusSection(
        getTranslation("legal.document.unavailable.title"),
        getTranslation("legal.document.unavailable.description")));
  }

  private Section statusSection(String title, String description) {
    Section section = new Section(
        new H1(title),
        new Paragraph(description),
        backToLogin());
    section.getElement().setAttribute("role", "status");
    return section;
  }

  private Anchor backToLogin() {
    return new Anchor("/login", getTranslation("legal.document.back-to-login"));
  }

  private String title(LegalDocumentContentVO document) {
    return switch (document.documentType()) {
      case TERMS_OF_USE -> getTranslation("legal.terms-of-use.title");
      case PRIVACY_POLICY -> getTranslation("legal.privacy-policy.title");
      case MARKETING -> getTranslation("legal.marketing.title");
    };
  }
}
