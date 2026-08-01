package br.com.rinos.app.ui.module.identity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.eng.rodrigogml.rfw.ui.content.RFWContentFormatEnum;
import br.eng.rodrigogml.rfw.ui.content.RFWContentRenderer;

@DisplayName("Rota pública de documento jurídico")
class LegalDocumentViewTest {

  /**
   * Apresenta o conteúdo íntegro usando o renderer Markdown sanitizado compartilhado.
   */
  @Test
  void setParameter_shouldRenderPublishedMarkdown_withRfwComponent() {
    LegalDocumentFacade facade = mock(LegalDocumentFacade.class);
    when(facade.findPublishedDocument("42")).thenReturn(Optional.of(
        new LegalDocumentContentVO(
            "42",
            LegalDocumentTypeEnum.TERMS_OF_USE,
            "1.0.0",
            "# Termos aprovados",
            Instant.parse("2026-07-29T12:00:00Z"))));
    LegalDocumentView view = new LegalDocumentView(facade);

    view.setParameter(null, "42");

    RFWContentRenderer renderer = descendants(view)
        .filter(RFWContentRenderer.class::isInstance)
        .map(RFWContentRenderer.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(renderer.getContent()).isEqualTo("# Termos aprovados");
    assertThat(renderer.getFormat()).isEqualTo(RFWContentFormatEnum.MARKDOWN);
    assertThat(renderer.isSanitizeHtml()).isTrue();
  }

  /**
   * Referência desconhecida gera estado neutro sem apresentar rascunho ou detalhe interno.
   */
  @Test
  void setParameter_shouldRenderNotFoundState_whenReferenceIsUnknown() {
    LegalDocumentFacade facade = mock(LegalDocumentFacade.class);
    when(facade.findPublishedDocument("999")).thenReturn(Optional.empty());
    LegalDocumentView view = new LegalDocumentView(facade);

    view.setParameter(null, "999");

    assertThat(descendants(view)
        .filter(H1.class::isInstance)
        .map(H1.class::cast)
        .map(H1::getText))
        .anySatisfy(title ->
            assertThat(title).contains("legal.document.not-found.title"));
  }

  /**
   * A rota e seu parâmetro permanecem públicos sem liberar outras áreas da aplicação.
   */
  @Test
  void route_shouldBeAnonymousAndStable() {
    Route route = LegalDocumentView.class.getAnnotation(Route.class);

    assertThat(route).isNotNull();
    assertThat(route.value()).isEqualTo("legal-document");
    assertThat(LegalDocumentView.class.isAnnotationPresent(AnonymousAllowed.class)).isTrue();
  }

  private static Stream<Component> descendants(Component component) {
    return Stream.concat(
        Stream.of(component),
        component.getChildren().flatMap(LegalDocumentViewTest::descendants));
  }
}
