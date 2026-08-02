package br.com.rinos.app.ui.module.identity.component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.ui.module.user.view.UserDashboardEntryView;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessCapabilityEnum;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessSlotEnum;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;
import br.eng.rodrigogml.rfw.ui.access.RFWLegalDocumentVO;
import br.eng.rodrigogml.rfw.ui.access.config.RFWAccessComponentConfig;

/**
 * Compõe a instância pública do componente de acesso usando somente APIs do RFW e do Rinos.
 *
 * <p>Rascunhos jurídicos nunca participam desta composição. A ausência de versões vigentes
 * desliga o cadastro e apresenta um estado seguro de indisponibilidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
@Lazy
public class RinosAccessComponentFactory {

  private static final String LEGAL_DOCUMENT_ROUTE = "/legal-document/";

  private final RFWAccessComponentFactory accessComponentFactory;
  private final LegalDocumentFacade legalDocumentFacade;

  /**
   * Cria a composição sobre a factory compartilhada e o contrato público de documentos.
   *
   * @param accessComponentFactory factory da plataforma
   * @param legalDocumentFacade consulta jurídica pública
   */
  public RinosAccessComponentFactory(
      RFWAccessComponentFactory accessComponentFactory,
      LegalDocumentFacade legalDocumentFacade) {
    this.accessComponentFactory = Objects.requireNonNull(
        accessComponentFactory,
        "accessComponentFactory must not be null");
    this.legalDocumentFacade = Objects.requireNonNull(
        legalDocumentFacade,
        "legalDocumentFacade must not be null");
  }

  /**
   * Cria o componente com a fotografia vigente ou bloqueia somente o cadastro.
   *
   * @param unavailableMessage mensagem localizada para o estado sem documentos
   * @return componente RFW pronto para a rota hospedeira
   */
  public RFWAccessComponent create(String unavailableMessage) {
    Objects.requireNonNull(unavailableMessage, "unavailableMessage must not be null");
    RFWAccessComponentConfig.Builder config = RFWAccessComponentConfig.builder()
        .onAuthenticated(ignored -> UI.getCurrent().navigate(UserDashboardEntryView.class))
        .fieldInstruction(
            RFWAccessStepEnum.REGISTRATION,
            "password",
            "registration.password-requirements");
    boolean registrationAvailable = configureLegalDocuments(config);
    if (!registrationAvailable) {
      config.disableCapability(RFWAccessCapabilityEnum.REGISTRATION);
    }

    RFWAccessComponent component = accessComponentFactory.create(config.build());
    if (!registrationAvailable) {
      Span feedback = new Span(unavailableMessage);
      feedback.getElement().setAttribute("role", "status");
      feedback.getElement().setAttribute("data-rinos-registration-unavailable", "true");
      component.setSlotContent(RFWAccessSlotEnum.BELOW_FIELDS, feedback);
    }
    return component;
  }

  private boolean configureLegalDocuments(RFWAccessComponentConfig.Builder config) {
    List<RFWLegalDocumentVO> documents = findCurrentLegalDocuments();
    config.legalDocumentsProvider(this::findCurrentLegalDocuments);
    return hasRequiredBaseline(documents);
  }

  private List<RFWLegalDocumentVO> findCurrentLegalDocuments() {
    try {
      return legalDocumentFacade.findCurrentDocuments().stream()
          .sorted(Comparator.comparingInt(
              document -> presentationOrder(document.documentType())))
          .map(RinosAccessComponentFactory::toRfwDocument)
          .toList();
    } catch (RuntimeException unavailableCatalog) {
      return List.of();
    }
  }

  private static boolean hasRequiredBaseline(List<RFWLegalDocumentVO> documents) {
    return documents.stream().anyMatch(document ->
        document.required() && document.labelKey().equals(labelKey(
            LegalDocumentTypeEnum.TERMS_OF_USE)))
        && documents.stream().anyMatch(document ->
            document.required() && document.labelKey().equals(labelKey(
                LegalDocumentTypeEnum.PRIVACY_POLICY)));
  }

  private static RFWLegalDocumentVO toRfwDocument(
      LegalDocumentReferenceVO document) {
    return new RFWLegalDocumentVO(
        document.reference(),
        labelKey(document.documentType()),
        LEGAL_DOCUMENT_ROUTE + document.reference(),
        document.required());
  }

  private static String labelKey(LegalDocumentTypeEnum type) {
    return switch (type) {
      case TERMS_OF_USE -> "legal.terms-of-use.acceptance";
      case PRIVACY_POLICY -> "legal.privacy-policy.acknowledgement";
      case MARKETING -> "legal.marketing.acceptance";
    };
  }

  private static int presentationOrder(LegalDocumentTypeEnum type) {
    return switch (type) {
      case TERMS_OF_USE -> 0;
      case PRIVACY_POLICY -> 1;
      case MARKETING -> 2;
    };
  }
}
