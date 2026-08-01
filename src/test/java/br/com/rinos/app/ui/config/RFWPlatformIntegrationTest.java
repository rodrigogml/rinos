package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import br.com.rinos.app.RinosApplication;
import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.com.rinos.app.ui.module.user.view.UserDashboardEntryView;
import br.eng.rodrigogml.rfw.config.RFWAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessCapabilityEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.google.config.RFWGoogleAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityResolver;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationRequirementProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWRegistrationProvider;
import br.eng.rodrigogml.rfw.authentication.google.RFWGoogleIdentityProvider;
import br.eng.rodrigogml.rfw.authentication.service.RFWAccessCapabilityService;
import br.eng.rodrigogml.rfw.authentication.service.RFWAuthenticationSessionService;
import br.eng.rodrigogml.rfw.authentication.turnstile.RFWTurnstileVerificationService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWActivationConsentChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;
import br.eng.rodrigogml.rfw.executioncontext.config.RFWExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.executioncontext.vaadin.config.RFWVaadinExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.i18n.config.RFWI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.i18n.config.RFWI18nPropertiesConfig;
import br.eng.rodrigogml.rfw.i18n.service.RFWTranslationService;
import br.eng.rodrigogml.rfw.i18n.vaadin.config.RFWVaadinI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.session.vaadin.config.RFWVaadinSessionAutoConfiguration;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessEntryRequestVO;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;
import br.eng.rodrigogml.rfw.ui.access.config.RFWAccessPropertiesConfig;
import br.eng.rodrigogml.rfw.ui.access.config.RFWAccessUIAutoConfiguration;
import br.eng.rodrigogml.rfw.ui.access.google.RFWGoogleSignInComponent;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;
import br.eng.rodrigogml.rfw.ui.theme.config.RFWThemeAutoConfiguration;
import br.eng.rodrigogml.rfw.ui.theme.config.UIThemePropertiesConfig;

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
          RFWAutoConfiguration.class,
          RFWAuthenticationAutoConfiguration.class,
          RFWGoogleAuthenticationAutoConfiguration.class,
          RFWThemeAutoConfiguration.class,
          RFWAccessUIAutoConfiguration.class))
      .withPropertyValues(
          "rfw.i18n.supported-language-locales=pt-BR",
          "rfw.i18n.default-language-locale=pt-BR",
          "rfw.i18n.default-format-locale=pt-BR",
          "rfw.i18n.default-zone-id=America/Sao_Paulo",
          "rfw.i18n.application-basenames=messages",
          "rfw.ui.theme.project-key=rinos",
          "rfw.ui.access.project-key=rinos",
          "rfw.ui.access.remember-me-enabled=false",
          "rfw.authentication.google.enabled=false",
          "rfw.authentication.turnstile.enabled=false");

  private final ApplicationContextRunner authenticationPropertiesRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(AuthenticationPropertiesTestConfig.class);

  @AfterEach
  void clearCurrentUi() {
    UI.setCurrent(null);
    VaadinSession.setCurrent(null);
    RequestContextHolder.resetRequestAttributes();
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
        .containsExactly("context://rfw/styles.css");
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
   * Exercita a entrada manual e o deep link de ativação sobre o componente público real.
   */
  @Test
  void activation_shouldRenderManualAndDeepLinkEntries_whenRegistrationIsAvailable() {
    RFWRegistrationProvider registrationProvider = mock(RFWRegistrationProvider.class);

    contextRunner
        .withBean(RFWRegistrationProvider.class, () -> registrationProvider)
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of(
              legalReference("21", LegalDocumentTypeEnum.TERMS_OF_USE, true),
              legalReference("22", LegalDocumentTypeEnum.PRIVACY_POLICY, true)));
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinSession session = new TestVaadinSession(mock(VaadinService.class));
          VaadinSession.setCurrent(session);
          RFWAccessComponent component = hostFactory.create("indisponível");

          component.open(new RFWAccessEntryRequestVO(
              RFWAccessStepEnum.ACTIVATION,
              null,
              "opaque-resume-proof"));

          assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.ACTIVATION);
          assertThat(descendants(component, TextField.class))
              .extracting(TextField::getLabel, TextField::getValue)
              .containsExactly(
                  org.assertj.core.groups.Tuple.tuple("E-mail ou usuário", ""),
                  org.assertj.core.groups.Tuple.tuple(
                      "Código de ativação",
                      "opaque-resume-proof"));
          assertThat(descendants(component, TextField.class).get(1).getElement()
              .getAttribute("autocomplete")).isEqualTo("one-time-code");
          assertThat(descendants(component, Button.class))
              .extracting(Button::getText)
              .contains(
                  "Ativar conta",
                  "Reenviar código de ativação",
                  "Voltar para entrar");
          assertThat(component.getElement().getText())
              .doesNotContain("opaque-resume-proof");

          component.open(new RFWAccessEntryRequestVO(
              RFWAccessStepEnum.ACTIVATION,
              null,
              null));

          assertThat(descendants(component, TextField.class).get(1).getValue()).isEmpty();
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
            "rfw.authentication.turnstile.enabled=true",
            "rfw.authentication.turnstile.site-key=test-site",
            "rfw.authentication.turnstile.secret-key=test-secret",
            "rfw.authentication.turnstile.expected-hostnames=rinos.test",
            "rfw.authentication.turnstile.site-verify-endpoint=http://127.0.0.1/siteverify",
            "rfw.authentication.turnstile.timeout=500ms")
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
            "rfw.authentication.turnstile.enabled=true",
            "rfw.authentication.turnstile.site-key=test-site",
            "rfw.authentication.turnstile.secret-key=test-secret",
            "rfw.authentication.turnstile.expected-hostnames=rinos.test")
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
   * Comprova a cadeia completa que inicia o Google no login quando o adapter real da hospedeira existe.
   */
  @Test
  void login_shouldRenderGoogleStart_whenIntegrationAndHostResolverArePresent() {
    RFWExternalIdentityResolverAdapter resolver = new RFWExternalIdentityResolverAdapter(
        mock(GoogleIdentityResolutionFacade.class));
    contextRunner
        .withBean(RFWExternalIdentityResolverAdapter.class, () -> resolver)
        .withPropertyValues(
            "rfw.authentication.google.enabled=true",
            "rfw.authentication.google.client-id=test-client",
            "rfw.authentication.google.issuer=https://accounts.google.com",
            "rfw.authentication.google.timeout=750ms",
            "rfw.authentication.google.clock-skew=45s")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(RFWExternalIdentityResolver.class);
          assertThat(context.getBean(RFWExternalIdentityResolver.class))
              .isSameAs(resolver);
          assertThat(context).hasSingleBean(RFWGoogleIdentityProvider.class);
          assertThat(context).hasSingleBean(RFWExternalIdentityProvider.class);
          assertThat(context.getBean(RFWExternalIdentityProvider.class))
              .isSameAs(context.getBean(RFWGoogleIdentityProvider.class));
          assertThat(context.getBean(RFWAccessCapabilityService.class)
              .getAvailableCapabilities())
              .contains(RFWAccessCapabilityEnum.EXTERNAL_IDENTITY);

          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of(
              legalReference("21", LegalDocumentTypeEnum.TERMS_OF_USE, true),
              legalReference("22", LegalDocumentTypeEnum.PRIVACY_POLICY, true)));
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinSession session = new TestVaadinSession(mock(VaadinService.class));
          VaadinSession.setCurrent(session);

          RFWAccessComponent component = hostFactory.create("indisponível");

          assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.SIGN_IN);
          assertThat(descendants(component, RFWGoogleSignInComponent.class))
              .singleElement()
              .satisfies(google -> assertThat(google.getElement().getTag())
                  .isEqualTo("rfw-google-sign-in"));
        });
  }

  /**
   * Comprova que a continuação tipada apresenta apenas e-mail verificado e documentos legais.
   */
  @Test
  void externalRegistration_shouldRenderMinimizedLegalContinuation_whenGoogleRequiresConsent() {
    GoogleIdentityResolutionFacade resolutionFacade = mock(GoogleIdentityResolutionFacade.class);
    Instant expiresAt = Instant.parse("2026-08-01T15:00:00Z");
    when(resolutionFacade.resolve(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleIdentityResolutionResultVO.continuation(
            "opaque-google-continuation",
            "google",
            "verified@example.com",
            expiresAt)));
    RFWExternalIdentityResolverAdapter resolver = new RFWExternalIdentityResolverAdapter(
        resolutionFacade);
    ExternalRegistrationFacade completionFacade = mock(ExternalRegistrationFacade.class);
    RinosUserPrincipalVO principal = new RinosUserPrincipalVO(41L, "verified@example.com");
    when(completionFacade.complete(any())).thenReturn(CompletableFuture.completedFuture(
        ExternalRegistrationCompletionResultVO.authenticated(principal)));
    RFWExternalRegistrationProviderAdapter completionProvider =
        new RFWExternalRegistrationProviderAdapter(completionFacade);
    RFWAuthenticationSessionService authenticationSessionService =
        mock(RFWAuthenticationSessionService.class);

    contextRunner
        .withBean(RFWExternalIdentityResolverAdapter.class, () -> resolver)
        .withBean(RFWExternalRegistrationProviderAdapter.class, () -> completionProvider)
        .withBean(RFWAuthenticationSessionService.class, () -> authenticationSessionService)
        .withPropertyValues(
            "rfw.authentication.google.enabled=true",
            "rfw.authentication.google.client-id=test-client")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(RFWAccessCapabilityService.class)
              .getAvailableCapabilities())
              .contains(
                  RFWAccessCapabilityEnum.EXTERNAL_IDENTITY,
                  RFWAccessCapabilityEnum.EXTERNAL_REGISTRATION);

          RFWAuthenticationOutcomeVO outcome = context
              .getBean(RFWExternalIdentityResolver.class)
              .resolve(new RFWVerifiedExternalIdentityVO(
                  "google",
                  "google-subject",
                  "verified@example.com",
                  true,
                  Map.of(
                      "iss", "https://accounts.google.com",
                      "name", "Google Profile Name",
                      "picture", "https://profiles.example/avatar.png")))
              .toCompletableFuture()
              .join();

          assertThat(outcome.status())
              .isEqualTo(RFWAccessStatusEnum.EXTERNAL_REGISTRATION_REQUIRED);
          assertThat(outcome.externalRegistration().expiresAt()).isEqualTo(expiresAt);

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

          component.openExternalRegistration(outcome.externalRegistration());

          assertThat(component.getCurrentStep())
              .isEqualTo(RFWAccessStepEnum.EXTERNAL_REGISTRATION);
          assertThat(descendants(component, EmailField.class))
              .singleElement()
              .satisfies(email -> {
                assertThat(email.getValue()).isEqualTo("verified@example.com");
                assertThat(email.isReadOnly()).isTrue();
              });
          assertThat(descendants(component, Checkbox.class))
              .extracting(checkbox -> checkbox.getElement()
                  .getAttribute("data-rfw-legal-document-id"))
              .containsExactly("21", "22", "23");
          assertThat(descendants(component, Anchor.class))
              .extracting(Anchor::getHref)
              .containsExactly(
                  "/legal-document/21",
                  "/legal-document/22",
                  "/legal-document/23");
          assertThat(descendants(component, PasswordField.class)).isEmpty();
          assertThat(descendants(component, TextField.class)).isEmpty();
          assertThat(component.getElement().getText())
              .doesNotContain(
                  "google-subject",
                  "https://accounts.google.com",
                  "Google Profile Name",
                  "https://profiles.example/avatar.png",
                  "opaque-google-continuation");

          UI ui = mock(UI.class);
          UI.setCurrent(ui);
          RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
              new MockHttpServletRequest(),
              new MockHttpServletResponse()));

          component.submitExternalRegistration(new RFWExternalRegistrationRequestDTO(
              "opaque-google-continuation",
              List.of("21", "22")));

          ArgumentCaptor<Authentication> authenticationCaptor =
              ArgumentCaptor.forClass(Authentication.class);
          InOrder authenticationOrder = inOrder(authenticationSessionService, ui);
          authenticationOrder.verify(authenticationSessionService).completeAuthentication(
              authenticationCaptor.capture(),
              eq(false));
          assertThat(authenticationCaptor.getValue().isAuthenticated()).isTrue();
          assertThat(authenticationCaptor.getValue().getPrincipal()).isEqualTo(principal);
          authenticationOrder.verify(ui).navigate(UserDashboardEntryView.class);
        });
  }

  /**
   * Comprova o binding explícito dos limites temporais usados em discovery e validação.
   */
  @Test
  void properties_shouldBindGoogleTimeoutAndClockSkew_whenExplicitValuesArePresent() {
    authenticationPropertiesRunner
        .withPropertyValues(
            "rfw.authentication.google.enabled=true",
            "rfw.authentication.google.client-id=test-client",
            "rfw.authentication.google.timeout=750ms",
            "rfw.authentication.google.clock-skew=45s")
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
