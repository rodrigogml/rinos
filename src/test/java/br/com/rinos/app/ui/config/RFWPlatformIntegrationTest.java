package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import br.com.rinos.app.RinosApplication;
import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.autoconfig.RFWPlatformAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.authentication.config.RFWAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAccessCapabilityEnum;
import br.eng.rodrigogml.rfw.platform.authentication.provider.RFWExternalIdentityProvider;
import br.eng.rodrigogml.rfw.platform.authentication.provider.RFWExternalIdentityResolver;
import br.eng.rodrigogml.rfw.platform.authentication.provider.RFWHumanVerificationProvider;
import br.eng.rodrigogml.rfw.platform.authentication.provider.RFWHumanVerificationRequirementProvider;
import br.eng.rodrigogml.rfw.platform.authentication.google.RFWGoogleIdentityProvider;
import br.eng.rodrigogml.rfw.platform.authentication.service.RFWAccessCapabilityService;
import br.eng.rodrigogml.rfw.platform.authentication.turnstile.RFWTurnstileVerificationService;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWActivationConsentChallengeVO;
import br.eng.rodrigogml.rfw.platform.executioncontext.config.RFWExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.executioncontext.vaadin.config.RFWVaadinExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.i18n.config.RFWI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.i18n.config.RFWI18nPropertiesConfig;
import br.eng.rodrigogml.rfw.platform.i18n.service.RFWTranslationService;
import br.eng.rodrigogml.rfw.platform.i18n.vaadin.config.RFWVaadinI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.session.vaadin.config.RFWVaadinSessionAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.platform.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.platform.ui.access.config.RFWAccessPropertiesConfig;
import br.eng.rodrigogml.rfw.platform.ui.access.config.RFWAccessUIAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.ui.access.provider.RFWRemoteAddressProvider;
import br.eng.rodrigogml.rfw.platform.ui.theme.config.UIThemePropertiesConfig;

@DisplayName("Integração pública com a RFW Platform")
class RFWPlatformIntegrationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          RFWI18nAutoConfiguration.class,
          RFWExecutionContextAutoConfiguration.class,
          RFWLoggingAutoConfiguration.class,
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

  private final ApplicationContextRunner authenticationPropertiesRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(AuthenticationPropertiesTestConfig.class);

  @AfterEach
  void clearCurrentUi() {
    UI.setCurrent(null);
    VaadinSession.setCurrent(null);
  }

  /**
   * Impede que a hospedeira deixe de carregar silenciosamente a folha agregada da plataforma.
   */
  @Test
  void appShell_shouldLoadPublicRfwStylesheet() {
    StyleSheet[] stylesheets = RinosApplication.class.getAnnotationsByType(StyleSheet.class);

    assertThat(RinosApplication.class)
        .isAssignableTo(AppShellConfigurator.class);
    assertThat(stylesheets)
        .extracting(StyleSheet::value)
        .containsExactly("context://rfw-platform/styles.css");
  }

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

  /**
   * Comprova que os adapters reais do Rinos são descobertos durante todo o ciclo local de cadastro.
   */
  @Test
  void context_shouldDiscoverRegistrationLifecycleCapabilities_whenRinosAdaptersExist() {
    RegistrationActivationFacade activationFacade =
        mock(RegistrationActivationFacade.class);
    RFWRegistrationProviderAdapter registration =
        new RFWRegistrationProviderAdapter(
            mock(RegistrationStartFacade.class),
            mock(RegistrationResendFacade.class),
            activationFacade,
            ignored -> "203.0.113.10");
    RFWActivationConsentProviderAdapter activationConsent =
        new RFWActivationConsentProviderAdapter(activationFacade);
    RFWRegistrationCancellationProviderAdapter cancellation =
        new RFWRegistrationCancellationProviderAdapter(
            mock(RegistrationCancellationFacade.class));

    contextRunner
        .withBean(RFWRegistrationProviderAdapter.class, () -> registration)
        .withBean(
            RFWActivationConsentProviderAdapter.class,
            () -> activationConsent)
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellation)
        .run(context -> {
          assertThat(context).hasNotFailed();
          RFWAccessCapabilityService capabilities =
              context.getBean(RFWAccessCapabilityService.class);

          assertThat(capabilities.getAvailableCapabilities()).contains(
              RFWAccessCapabilityEnum.REGISTRATION,
              RFWAccessCapabilityEnum.ACTIVATION_CONSENT,
              RFWAccessCapabilityEnum.REGISTRATION_CANCELLATION);
          assertThat(capabilities.registrationProvider()).contains(registration);
          assertThat(capabilities.activationConsentProvider())
              .contains(activationConsent);
          assertThat(capabilities.registrationCancellationProvider())
              .contains(cancellation);
        });
  }

  /**
   * Exercita o renderer público real com o e-mail minimizado e a seleção jurídica do desafio.
   */
  @Test
  void activationConsent_shouldShowSafeReadOnlyEmailAndOnlyChallengedDocuments() {
    RegistrationActivationFacade activationFacade =
        mock(RegistrationActivationFacade.class);
    RFWActivationConsentProviderAdapter activationConsent =
        new RFWActivationConsentProviderAdapter(activationFacade);

    contextRunner
        .withBean(
            RFWActivationConsentProviderAdapter.class,
            () -> activationConsent)
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of(
              legalReference("21", LegalDocumentTypeEnum.TERMS_OF_USE, true),
              legalReference("22", LegalDocumentTypeEnum.PRIVACY_POLICY, true),
              legalReference("23", LegalDocumentTypeEnum.MARKETING, false)));
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinSession session = new TestVaadinSession(mock(VaadinService.class));
          VaadinSession.setCurrent(session);
          RFWAccessComponent component = hostFactory.create("indisponível");

          component.openActivationConsent(new RFWActivationConsentChallengeVO(
              "opaque-activation-proof",
              "p***@example.com",
              Set.of("22"),
              Instant.parse("2026-07-30T15:00:00Z")));

          assertThat(descendants(component, EmailField.class)).singleElement()
              .satisfies(email -> {
                assertThat(email.getValue()).isEqualTo("p***@example.com");
                assertThat(email.isReadOnly()).isTrue();
              });
          assertThat(descendants(component, Checkbox.class))
              .extracting(checkbox -> checkbox.getElement()
                  .getAttribute("data-rfw-legal-document-id"))
              .containsExactly("22");
          assertThat(descendants(component, Anchor.class))
              .extracting(anchor -> anchor.getElement().getAttribute("href"))
              .containsExactly("/legal-document/22");
          assertThat(descendants(component, Paragraph.class))
              .filteredOn(paragraph -> paragraph.getElement()
                  .hasAttribute("data-rfw-activation-expiration"))
              .singleElement()
              .satisfies(expiration -> {
                assertThat(expiration.getText())
                    .contains("30/07/2026", "12:00");
                assertThat(expiration.getElement().getAttribute("role"))
                    .isEqualTo("status");
                assertThat(expiration.getElement().getAttribute("aria-live"))
                    .isEqualTo("polite");
              });
          assertThat(component.getElement().getText())
              .doesNotContain("person@example.com", "opaque-activation-proof");
        });
  }

  /**
   * Comprova que todos os estados públicos desta etapa possuem texto localizado na hospedeira.
   */
  @Test
  void context_shouldResolveActivationAndSmtpFailureMessages() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      RFWTranslationService translations = context.getBean(RFWTranslationService.class);

      assertThat(List.of(
          "registration.activation.invalid-proof",
          "registration.activation.expired-proof",
          "registration.activation.used-proof",
          "registration.activation.registration-closed",
          "registration.email-dispatch-failed",
          "registration.resend-email-dispatch-failed"))
          .allSatisfy(key -> assertThat(
              translations.translate(key, Locale.of("pt", "BR")))
              .doesNotStartWith("!")
              .isNotEqualTo(key));
    });
  }

  /**
   * Comprova que a configuração explícita habilita o adapter técnico do RFW e conecta as políticas do Rinos.
   */
  @Test
  void context_shouldWireTurnstileAndHostPolicies_whenIntegrationIsEnabled() {
    HumanVerificationPolicyFacade facade = mock(HumanVerificationPolicyFacade.class);
    contextRunner
        .withBean(RFWRemoteAddressProvider.class,
            () -> new RFWRemoteAddressProviderAdapter(facade))
        .withBean(RFWHumanVerificationRequirementProvider.class,
            () -> new RFWHumanVerificationRequirementProviderAdapter(facade))
        .withPropertyValues(
            "rfw.platform.authentication.turnstile.enabled=true",
            "rfw.platform.authentication.turnstile.site-key=test-site",
            "rfw.platform.authentication.turnstile.secret-key=test-secret",
            "rfw.platform.authentication.turnstile.expected-hostnames=rinos.test",
            "rfw.platform.authentication.turnstile.site-verify-endpoint=http://127.0.0.1/siteverify",
            "rfw.platform.authentication.turnstile.timeout=500ms")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(RFWHumanVerificationProvider.class);
          assertThat(context.getBean(RFWHumanVerificationProvider.class))
              .isInstanceOf(RFWTurnstileVerificationService.class);
          assertThat(context).hasSingleBean(RFWHumanVerificationRequirementProvider.class);
          assertThat(context).hasSingleBean(RFWRemoteAddressProvider.class);
          assertThat(context).hasSingleBean(RFWAccessComponentFactory.class);
        });
  }

  /**
   * Comprova diretamente o binding dos valores privados e públicos exigidos pelo adapter padrão.
   */
  @Test
  void properties_shouldBindTurnstileSecrets_whenExplicitFileValuesArePresent() {
    authenticationPropertiesRunner
        .withPropertyValues(
            "rfw.platform.authentication.turnstile.enabled=true",
            "rfw.platform.authentication.turnstile.site-key=test-site",
            "rfw.platform.authentication.turnstile.secret-key=test-secret",
            "rfw.platform.authentication.turnstile.expected-hostnames=rinos.test")
        .run(context -> {
          assertThat(context).hasNotFailed();
          RFWAuthenticationPropertiesConfig.TurnstileConfig turnstile = context
              .getBean(RFWAuthenticationPropertiesConfig.class)
              .turnstile();
          assertThat(turnstile.enabled()).isTrue();
          assertThat(turnstile.siteKey()).isEqualTo("test-site");
          assertThat(turnstile.secretKey()).isEqualTo("test-secret");
          assertThat(turnstile.expectedHostnames()).containsExactly("rinos.test");
        });
  }

  /**
   * Comprova que o Google somente é anunciado quando a hospedeira fornece um resolvedor real.
   */
  @Test
  void context_shouldWireGoogleProvider_whenIntegrationAndHostResolverArePresent() {
    RFWExternalIdentityResolver resolver = mock(RFWExternalIdentityResolver.class);
    contextRunner
        .withBean(RFWExternalIdentityResolver.class, () -> resolver)
        .withPropertyValues(
            "rfw.platform.authentication.google.enabled=true",
            "rfw.platform.authentication.google.client-id=test-client",
            "rfw.platform.authentication.google.issuer=https://accounts.google.com",
            "rfw.platform.authentication.google.timeout=750ms",
            "rfw.platform.authentication.google.clock-skew=45s")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(RFWGoogleIdentityProvider.class);
          assertThat(context).hasSingleBean(RFWExternalIdentityProvider.class);
          assertThat(context.getBean(RFWExternalIdentityProvider.class))
              .isSameAs(context.getBean(RFWGoogleIdentityProvider.class));
          assertThat(context.getBean(RFWAccessCapabilityService.class)
              .getAvailableCapabilities())
              .contains(RFWAccessCapabilityEnum.EXTERNAL_IDENTITY);
        });
  }

  /**
   * Comprova o binding explícito dos limites temporais usados em discovery e validação.
   */
  @Test
  void properties_shouldBindGoogleTimeoutAndClockSkew_whenExplicitValuesArePresent() {
    authenticationPropertiesRunner
        .withPropertyValues(
            "rfw.platform.authentication.google.enabled=true",
            "rfw.platform.authentication.google.client-id=test-client",
            "rfw.platform.authentication.google.timeout=750ms",
            "rfw.platform.authentication.google.clock-skew=45s")
        .run(context -> {
          assertThat(context).hasNotFailed();
          RFWAuthenticationPropertiesConfig.GoogleConfig google = context
              .getBean(RFWAuthenticationPropertiesConfig.class)
              .google();
          assertThat(google.timeout()).isEqualTo(Duration.ofMillis(750));
          assertThat(google.clockSkew()).isEqualTo(Duration.ofSeconds(45));
        });
  }

  private static LegalDocumentReferenceVO legalReference(
      String reference,
      LegalDocumentTypeEnum type,
      boolean required) {
    return new LegalDocumentReferenceVO(reference, type, "1.0.0", required);
  }

  /**
   * Percorre a árvore Vaadin preservando a ordem de apresentação.
   *
   * @param root raiz da busca
   * @param type tipo procurado
   * @param <T> tipo de componente
   * @return componentes encontrados na ordem visual
   */
  private static <T extends Component> List<T> descendants(
      Component root,
      Class<T> type) {
    Stream<T> current = type.isInstance(root)
        ? Stream.of(type.cast(root))
        : Stream.empty();
    return Stream.concat(
        current,
        root.getChildren().flatMap(child -> descendants(child, type).stream()))
        .toList();
  }

  /**
   * Fornece o lock mínimo de sessão exigido pelos componentes Vaadin no teste isolado.
   */
  private static final class TestVaadinSession extends VaadinSession {

    private static final long serialVersionUID = 1L;

    private final ReentrantLock lock = new ReentrantLock();

    private TestVaadinSession(VaadinService service) {
      super(service);
    }

    @Override
    public Lock getLockInstance() {
      return lock;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(RFWAuthenticationPropertiesConfig.class)
  static class AuthenticationPropertiesTestConfig {
  }
}
