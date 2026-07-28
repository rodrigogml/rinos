package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.eng.rodrigogml.rfw.platform.autoconfig.RFWPlatformAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.authentication.config.RFWAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAccessCapabilityEnum;
import br.eng.rodrigogml.rfw.platform.authentication.service.RFWAccessCapabilityService;
import br.eng.rodrigogml.rfw.platform.executioncontext.config.RFWExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.executioncontext.vaadin.config.RFWVaadinExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.i18n.config.RFWI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.i18n.config.RFWI18nPropertiesConfig;
import br.eng.rodrigogml.rfw.platform.i18n.vaadin.config.RFWVaadinI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.session.vaadin.config.RFWVaadinSessionAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.platform.ui.access.config.RFWAccessPropertiesConfig;
import br.eng.rodrigogml.rfw.platform.ui.access.config.RFWAccessUIAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.ui.theme.config.UIThemePropertiesConfig;

@DisplayName("Integração pública com a RFW Platform")
class RFWPlatformIntegrationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          RFWI18nAutoConfiguration.class,
          RFWExecutionContextAutoConfiguration.class,
          RFWVaadinSessionAutoConfiguration.class,
          RFWVaadinI18nAutoConfiguration.class,
          RFWVaadinExecutionContextAutoConfiguration.class,
          RFWPlatformAutoConfiguration.class,
          RFWAuthenticationAutoConfiguration.class,
          RFWAccessUIAutoConfiguration.class))
      .withPropertyValues(
          "rfw.platform.i18n.supported-language-locales=pt-BR",
          "rfw.platform.i18n.default-language-locale=pt-BR",
          "rfw.platform.i18n.default-format-locale=pt-BR",
          "rfw.platform.i18n.default-zone-id=America/Sao_Paulo",
          "rfw.platform.i18n.application-basenames=messages",
          "rfw.platform.ui.theme.project-key=rinos",
          "rfw.platform.ui.access.project-key=rinos",
          "rfw.platform.ui.access.remember-me-enabled=false",
          "rfw.platform.authentication.google.enabled=false",
          "rfw.platform.authentication.turnstile.enabled=false");

  /**
   * Comprova que a hospedeira recebe a factory pública e os padrões globais configurados.
   */
  @Test
  void context_shouldProvidePublicFactoryAndRinosDefaults_whenRfwIsAutoConfigured() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(RFWAccessComponentFactory.class);

      RFWI18nPropertiesConfig i18n = context.getBean(RFWI18nPropertiesConfig.class);
      UIThemePropertiesConfig theme = context.getBean(UIThemePropertiesConfig.class);
      RFWAccessPropertiesConfig access = context.getBean(RFWAccessPropertiesConfig.class);

      assertThat(i18n.getSupportedLanguageLocales()).containsExactly(Locale.of("pt", "BR"));
      assertThat(i18n.getDefaultLanguageLocale()).isEqualTo(Locale.of("pt", "BR"));
      assertThat(i18n.getDefaultFormatLocale()).isEqualTo(Locale.of("pt", "BR"));
      assertThat(i18n.getDefaultZoneId()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
      assertThat(i18n.getApplicationBasenames()).containsExactly("messages");
      assertThat(theme.getProjectKey()).isEqualTo("rinos");
      assertThat(access.projectKey()).isEqualTo("rinos");
      assertThat(access.rememberMeEnabled()).isFalse();
    });
  }

  /**
   * Comprova que capacidades de negócio não aparecem antes do registro de providers reais.
   */
  @Test
  void context_shouldNotAdvertiseBusinessCapabilities_whenHostProvidersAreAbsent() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();

      Set<RFWAccessCapabilityEnum> capabilities = context
          .getBean(RFWAccessCapabilityService.class)
          .getAvailableCapabilities();

      assertThat(capabilities).isEmpty();
    });
  }
}
