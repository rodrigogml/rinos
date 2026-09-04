package br.com.rinos.app.ui.module.access.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Component;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationSnapshot;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationCapabilities;
import br.com.rinos.app.api.module.access.vo.AccessCategoryItem;
import br.com.rinos.app.api.module.access.vo.AccessKeyItem;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.ui.config.SpringAccessAdministrationAdapter;
import br.eng.rodrigogml.rfw.i18n.service.RFWTranslationService;
import br.eng.rodrigogml.rfw.logging.RFWLogger;
import br.eng.rodrigogml.rfw.spring.SpringContext;
import br.eng.rodrigogml.rfw.authentication.provider.RFWReauthenticationChallengeProvider;
import org.springframework.context.ApplicationContext;
import java.util.Locale;
import java.util.ResourceBundle;

class AccessCenterComponentTest {

  @BeforeEach
  void prepareTranslationContext() {
    ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.ROOT);
    RFWTranslationService translations = new RFWTranslationService(
        null, null, mock(RFWLogger.class)) {
      @Override
      public String translate(String key, Locale locale, Object... params) {
        return messages.containsKey(key) ? messages.getString(key) : "!" + key;
      }
    };
    ApplicationContext context = mock(ApplicationContext.class);
    when(context.getBean(RFWTranslationService.class)).thenReturn(translations);
    new SpringContext(context);
  }

  @AfterEach
  void clearUi() {
    UI.setCurrent(null);
  }

  @Test
  void load_shouldBuildResponsiveRfwCompositionWithoutRenderingTechnicalCode() {
    UI ui = new UI();
    UI.setCurrent(ui);
    SpringAccessAdministrationAdapter administration =
        mock(SpringAccessAdministrationAdapter.class);
    String technicalCode = "tenant.access.rule.view";
    when(administration.inspect(ui)).thenReturn(new AccessAdministrationSnapshot(
        AuthorizationContext.tenant(7L).withRevision(2L), 2L,
        new AccessAdministrationCapabilities(true, true, true, true, true, true),
        List.of(new AccessCategoryItem(
            "tenant.foundation.access", null,
            "access.category.tenant.foundation.access.name")),
        List.of(new AccessKeyItem(
            technicalCode, "tenant.foundation.access",
            "access.key.tenant.access.rule.view.name",
            "access.key.tenant.access.rule.view.description",
            AccessAdministrationState.ACTIVE, true)),
        List.of(), List.of(), List.of(), List.of()));
    AccessCenterComponent component = new AccessCenterComponent(administration,
        mock(RFWReauthenticationChallengeProvider.class));

    component.load(ui);

    assertThat(component.getElement().getText()).doesNotContain(technicalCode);
    assertThat(component.getElement().getStyle().get("width")).isEqualTo("100%");
    assertThat(hasId(component, "access-center-search")).isTrue();
    assertThat(hasId(component, "access-center-categories")).isTrue();
  }

  private static boolean hasId(Component component, String id) {
    return component.getId().filter(id::equals).isPresent()
        || component.getChildren().anyMatch(child -> hasId(child, id));
  }
}
