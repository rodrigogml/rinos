package br.com.rinos.app.ui.module.identity.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessCapabilityEnum;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessSlotEnum;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;
import br.eng.rodrigogml.rfw.ui.access.config.RFWAccessComponentConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("Composição do acesso Rinos sobre o RFW")
class RinosAccessComponentFactoryTest {

  @Mock
  private RFWAccessComponentFactory rfwFactory;

  @Mock
  private LegalDocumentFacade legalDocumentFacade;

  @Mock
  private RFWAccessComponent accessComponent;

  private RinosAccessComponentFactory factory;

  @BeforeEach
  void setUp() {
    factory = new RinosAccessComponentFactory(rfwFactory, legalDocumentFacade);
    when(rfwFactory.create(any(RFWAccessComponentConfig.class)))
        .thenReturn(accessComponent);
  }

  /**
   * Configura os dois documentos-base em ordem humana e preserva suas referências reais.
   */
  @Test
  void create_shouldConfigureCurrentDocuments_whenRequiredBaselineIsAvailable() {
    when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of(
        reference("22", LegalDocumentTypeEnum.PRIVACY_POLICY, true),
        reference("21", LegalDocumentTypeEnum.TERMS_OF_USE, true)));

    Component result = factory.create("indisponível");

    assertThat(result).isSameAs(accessComponent);
    ArgumentCaptor<RFWAccessComponentConfig> configCaptor =
        ArgumentCaptor.forClass(RFWAccessComponentConfig.class);
    verify(rfwFactory).create(configCaptor.capture());
    RFWAccessComponentConfig config = configCaptor.getValue();
    assertThat(config.getDisabledCapabilities()).doesNotContain(
        RFWAccessCapabilityEnum.REGISTRATION);
    assertThat(config.getFieldInstructionKey(
        RFWAccessStepEnum.REGISTRATION,
        "password")).isEqualTo("registration.password-requirements");
    assertThat(config.getLegalDocuments()).extracting(
        document -> document.id(),
        document -> document.labelKey(),
        document -> document.url(),
        document -> document.required())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "21",
                "legal.terms-of-use.acceptance",
                "/legal-document/21",
                true),
            org.assertj.core.groups.Tuple.tuple(
                "22",
                "legal.privacy-policy.acknowledgement",
                "/legal-document/22",
                true));
    verify(accessComponent, never()).setSlotContent(
        any(RFWAccessSlotEnum.class),
        any(Component[].class));
  }

  /**
   * Reconsulta o catálogo para que uma versão publicada durante a sessão substitua a anterior.
   */
  @Test
  void create_shouldRefreshLegalDocuments_whenCatalogChangesAfterComposition() {
    when(legalDocumentFacade.findCurrentDocuments()).thenReturn(
        List.of(
            reference("terms-v1", LegalDocumentTypeEnum.TERMS_OF_USE, true),
            reference("privacy-v1", LegalDocumentTypeEnum.PRIVACY_POLICY, true)),
        List.of(
            reference("terms-v2", LegalDocumentTypeEnum.TERMS_OF_USE, true),
            reference("privacy-v2", LegalDocumentTypeEnum.PRIVACY_POLICY, true)));

    factory.create("indisponível");

    ArgumentCaptor<RFWAccessComponentConfig> configCaptor =
        ArgumentCaptor.forClass(RFWAccessComponentConfig.class);
    verify(rfwFactory).create(configCaptor.capture());
    assertThat(configCaptor.getValue().getLegalDocuments())
        .extracting(document -> document.id())
        .containsExactly("terms-v2", "privacy-v2");
  }

  /**
   * A falta de qualquer documento-base obrigatório fecha somente a capacidade de cadastro.
   */
  @Test
  void create_shouldDisableRegistrationAndShowStatus_whenBaselineIsIncomplete() {
    when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of(
        reference("21", LegalDocumentTypeEnum.TERMS_OF_USE, true)));

    factory.create("Documentos ainda não publicados.");

    ArgumentCaptor<RFWAccessComponentConfig> configCaptor =
        ArgumentCaptor.forClass(RFWAccessComponentConfig.class);
    verify(rfwFactory).create(configCaptor.capture());
    assertThat(configCaptor.getValue().getDisabledCapabilities())
        .contains(RFWAccessCapabilityEnum.REGISTRATION);

    ArgumentCaptor<Component[]> componentsCaptor =
        ArgumentCaptor.forClass(Component[].class);
    verify(accessComponent).setSlotContent(
        org.mockito.ArgumentMatchers.eq(RFWAccessSlotEnum.BELOW_FIELDS),
        componentsCaptor.capture());
    assertThat(componentsCaptor.getValue()).singleElement()
        .isInstanceOfSatisfying(Span.class, feedback -> {
          assertThat(feedback.getText()).isEqualTo("Documentos ainda não publicados.");
          assertThat(feedback.getElement().getAttribute("role")).isEqualTo("status");
          assertThat(feedback.getElement().getAttribute(
              "data-rinos-registration-unavailable")).isEqualTo("true");
        });
  }

  /**
   * Indisponibilidade do banco não permite apresentar cadastro sem documentos.
   */
  @Test
  void create_shouldFailClosed_whenLegalCatalogCannotBeRead() {
    when(legalDocumentFacade.findCurrentDocuments())
        .thenThrow(new IllegalStateException("database unavailable"));

    factory.create("Tente novamente.");

    ArgumentCaptor<RFWAccessComponentConfig> configCaptor =
        ArgumentCaptor.forClass(RFWAccessComponentConfig.class);
    verify(rfwFactory).create(configCaptor.capture());
    assertThat(configCaptor.getValue().getLegalDocuments()).isEmpty();
    assertThat(configCaptor.getValue().getDisabledCapabilities())
        .contains(RFWAccessCapabilityEnum.REGISTRATION);
  }

  private static LegalDocumentReferenceVO reference(
      String reference,
      LegalDocumentTypeEnum type,
      boolean required) {
    return new LegalDocumentReferenceVO(reference, type, "1.0.0", required);
  }
}
