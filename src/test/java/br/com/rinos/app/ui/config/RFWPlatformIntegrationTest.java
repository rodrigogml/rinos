package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
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
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import br.com.rinos.app.RinosApplication;
import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationRequestStatusEnum;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.facade.AuthenticationConsentFacade;
import br.com.rinos.app.api.facade.HumanVerificationPolicyFacade;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.facade.PasswordAuthenticationFacade;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.facade.ReauthenticationFacade;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;
import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.com.rinos.app.ui.module.user.view.UserDashboardEntryView;
import br.eng.rodrigogml.rfw.config.RFWAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWPasswordAuthenticationRequestDTO;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessCapabilityEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWHumanVerificationOperationEnum;
import br.eng.rodrigogml.rfw.authentication.google.config.RFWGoogleAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityResolver;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWHumanVerificationRequirementProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPasswordAuthenticationProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWRegistrationProvider;
import br.eng.rodrigogml.rfw.authentication.google.RFWGoogleIdentityProvider;
import br.eng.rodrigogml.rfw.authentication.service.RFWAccessCapabilityService;
import br.eng.rodrigogml.rfw.authentication.service.RFWAuthenticationSessionService;
import br.eng.rodrigogml.rfw.authentication.turnstile.RFWTurnstileVerificationService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWActivationConsentChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWHumanVerificationRequestVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWHumanVerificationResultVO;
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
import br.eng.rodrigogml.rfw.ui.access.turnstile.RFWTurnstileComponent;
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
   * Comprova a descoberta dos providers reais dos gates posteriores aos fatores de acesso.
   */
  @Test
  void context_shouldDiscoverLegalConsentAndReauthenticationCapabilities() {
    RFWAuthenticationConsentProviderAdapter authenticationConsent =
        new RFWAuthenticationConsentProviderAdapter(
            mock(AuthenticationConsentFacade.class),
            new RFWAuthenticationOutcomeAdapter());
    RFWReauthenticationChallengeProviderAdapter reauthentication =
        new RFWReauthenticationChallengeProviderAdapter(
            mock(ReauthenticationFacade.class));

    contextRunner
        .withBean(
            RFWAuthenticationConsentProviderAdapter.class,
            () -> authenticationConsent)
        .withBean(
            RFWReauthenticationChallengeProviderAdapter.class,
            () -> reauthentication)
        .run(context -> {
          assertThat(context).hasNotFailed();
          RFWAccessCapabilityService capabilities =
              context.getBean(RFWAccessCapabilityService.class);

          assertThat(capabilities.getAvailableCapabilities()).contains(
              RFWAccessCapabilityEnum.AUTHENTICATION_CONSENT,
              RFWAccessCapabilityEnum.REAUTHENTICATION);
          assertThat(capabilities.authenticationConsentProvider())
              .contains(authenticationConsent);
          assertThat(capabilities.reauthenticationChallengeProvider())
              .contains(reauthentication);
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
   * Comprova a composição do cancelamento a partir da ativação sem transportar a prova efêmera.
   */
  @Test
  void activation_shouldOpenCancellationRequestWithCurrentIdentifier_whenCapabilityIsAvailable() {
    RFWRegistrationProvider registrationProvider = mock(RFWRegistrationProvider.class);
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(
            mock(RegistrationCancellationFacade.class));

    contextRunner
        .withBean(RFWRegistrationProvider.class, () -> registrationProvider)
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
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
              "previous@example.com",
              "opaque-activation-proof"));

          TextField identifier = descendants(component, TextField.class).getFirst();
          identifier.setValue("current@example.com");
          descendants(component, Button.class).stream()
              .filter(button -> "Cancelar cadastro pendente".equals(button.getText()))
              .findFirst()
              .orElseThrow()
              .click();

          assertThat(component.getCurrentStep())
              .isEqualTo(RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST);
          assertThat(component.getEntryRequest()).isEqualTo(new RFWAccessEntryRequestVO(
              RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
              "current@example.com",
              null));
          assertThat(descendants(component, TextField.class))
              .extracting(TextField::getLabel, TextField::getValue)
              .containsExactly(org.assertj.core.groups.Tuple.tuple(
                  "E-mail ou usuário",
                  "current@example.com"));
          assertThat(component.getElement().getText())
              .doesNotContain("opaque-activation-proof");
        });
  }

  /**
   * Comprova a consequência anterior à solicitação e a resposta condicional após um resultado neutro.
   */
  @Test
  void cancellationRequest_shouldExplainConsequencesAndKeepConfirmationResponseNeutral() {
    RegistrationCancellationFacade cancellationFacade =
        mock(RegistrationCancellationFacade.class);
    when(cancellationFacade.requestCancellation(any())).thenReturn(
        CompletableFuture.completedFuture(new RegistrationCancellationRequestResultVO(
            RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
            "neutral-reference",
            Instant.parse("2026-08-01T18:00:00Z"),
            Map.of())));
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(cancellationFacade);

    contextRunner
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of());
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            RFWAccessComponent component = hostFactory.create("indisponível");
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            ui.add(component);
            component.open(new RFWAccessEntryRequestVO(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
                "person@example.com",
                null));

            assertThat(descendants(component, Paragraph.class))
                .extracting(Paragraph::getText)
                .contains("Solicitar as instruções não cancela o cadastro. Se o cancelamento "
                    + "for confirmado, o cadastro pendente será excluído, seus links e códigos "
                    + "de ativação deixarão de funcionar e o e-mail poderá ser usado em um novo "
                    + "cadastro. O cancelamento confirmado não pode ser desfeito.");

            descendants(component, Button.class).stream()
                .filter(button -> "Solicitar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            assertThat(component.getCurrentStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
            assertThat(component.getCurrentChallenge().maskedDestination()).isNull();
            assertThat(descendants(component, Paragraph.class))
                .extracting(Paragraph::getText)
                .contains("Um código válido confirma a exclusão definitiva do cadastro pendente. "
                    + "Depois da confirmação, os links e códigos de ativação deixarão de funcionar "
                    + "e o e-mail poderá ser usado em um novo cadastro. A ação Confirmar "
                    + "cancelamento não pode ser desfeita.");
            assertThat(descendants(component, Button.class))
                .extracting(Button::getText)
                .contains("Confirmar cancelamento");
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que a operação específica do Turnstile precede o adapter real de cancelamento.
   */
  @Test
  void cancellationRequest_shouldVerifyTurnstileContextBeforeCallingHostProvider() {
    AtomicReference<RFWHumanVerificationRequestVO> verificationRequest =
        new AtomicReference<>();
    RFWHumanVerificationProvider verificationProvider =
        new RFWHumanVerificationProvider() {
          @Override
          public CompletionStage<RFWHumanVerificationResultVO> verify(
              String token,
              String remoteAddress) {
            throw new AssertionError("A verificação contextualizada deve ser utilizada.");
          }

          @Override
          public CompletionStage<RFWHumanVerificationResultVO> verify(
              RFWHumanVerificationRequestVO request) {
            verificationRequest.set(request);
            return CompletableFuture.completedFuture(new RFWHumanVerificationResultVO(
                true,
                "rinos.test",
                Instant.parse("2026-08-01T18:00:00Z"),
                List.of()));
          }
        };
    RegistrationCancellationFacade cancellationFacade =
        mock(RegistrationCancellationFacade.class);
    when(cancellationFacade.requestCancellation(any())).thenReturn(
        CompletableFuture.completedFuture(new RegistrationCancellationRequestResultVO(
            RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
            "neutral-reference",
            Instant.parse("2026-08-02T18:00:00Z"),
            Map.of())));
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(cancellationFacade);

    contextRunner
        .withBean(RFWHumanVerificationProvider.class, () -> verificationProvider)
        .withBean(RFWHumanVerificationRequirementProvider.class, () ->
            (operation, remoteAddress) -> true)
        .withBean(RFWRemoteAddressProvider.class, () ->
            ignored -> "203.0.113.10")
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
        .withPropertyValues(
            "rfw.authentication.turnstile.enabled=true",
            "rfw.authentication.turnstile.site-key=test-site")
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of());
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
            RFWAccessComponent component = hostFactory.create("indisponível");
            ui.add(component);
            component.open(new RFWAccessEntryRequestVO(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
                "person@example.com",
                null));
            RFWTurnstileComponent turnstile = descendants(
                component,
                RFWTurnstileComponent.class).getFirst();
            turnstile.acceptToken("cancellation-token");

            descendants(component, Button.class).stream()
                .filter(button -> "Solicitar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            assertThat(verificationRequest.get()).isNotNull();
            assertThat(verificationRequest.get().token()).isEqualTo("cancellation-token");
            assertThat(verificationRequest.get().remoteAddress()).isEqualTo("203.0.113.10");
            assertThat(verificationRequest.get().operation())
                .isEqualTo(RFWHumanVerificationOperationEnum.REGISTRATION_CANCELLATION);
            assertThat(verificationRequest.get().action())
                .isEqualTo("registration-cancellation");
            verify(cancellationFacade).requestCancellation(any());
            assertThat(component.getCurrentStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que a política contextual e a validação fail-closed antecedem a fachada de senha.
   */
  @Test
  void passwordAuthentication_shouldNotReachHostFacadeWhenRequiredTurnstileIsRejected() {
    AtomicReference<String> policyIdentifier = new AtomicReference<>();
    RFWHumanVerificationRequirementProvider requirementProvider =
        new RFWHumanVerificationRequirementProvider() {
          @Override
          public boolean isRequired(
              RFWHumanVerificationOperationEnum operation,
              String remoteAddress) {
            return true;
          }

          @Override
          public boolean isRequired(
              RFWHumanVerificationOperationEnum operation,
              String remoteAddress,
              String identifier) {
            policyIdentifier.set(identifier);
            return true;
          }
        };
    RFWHumanVerificationProvider verificationProvider = (token, remoteAddress) ->
        CompletableFuture.completedFuture(new RFWHumanVerificationResultVO(
            false,
            "rinos.test",
            Instant.parse("2026-08-09T12:00:00Z"),
            List.of("invalid-input-response")));
    PasswordAuthenticationFacade passwordFacade = mock(PasswordAuthenticationFacade.class);
    RFWPasswordAuthenticationProvider passwordProvider =
        new RFWPasswordAuthenticationProviderAdapter(
            passwordFacade,
            ignored -> "203.0.113.10",
            new RFWAuthenticationOutcomeAdapter());

    contextRunner
        .withBean(RFWHumanVerificationProvider.class, () -> verificationProvider)
        .withBean(RFWHumanVerificationRequirementProvider.class, () -> requirementProvider)
        .withBean(RFWRemoteAddressProvider.class, () -> ignored -> "203.0.113.10")
        .withBean(RFWPasswordAuthenticationProvider.class, () -> passwordProvider)
        .withPropertyValues(
            "rfw.authentication.turnstile.enabled=true",
            "rfw.authentication.turnstile.site-key=test-site")
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of());
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
            RFWAccessComponent component = hostFactory.create("indisponível");
            ui.add(component);

            component.submitPasswordAuthentication(
                new RFWPasswordAuthenticationRequestDTO(
                    "person@example.test", "Password1!", false, "invalid-token"));

            assertThat(policyIdentifier.get()).isEqualTo("person@example.test");
            assertThat(component.getCurrentOutcome().status())
                .isEqualTo(RFWAccessStatusEnum.REJECTED);
            verify(passwordFacade, never()).authenticate(any());
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que uma rejeição recuperável retém somente o identificador e exige nova prova humana.
   */
  @Test
  void cancellationRequest_shouldPreserveIdentifierAndNeverReuseConsumedTurnstileToken() {
    List<String> verifiedTokens = new ArrayList<>();
    RFWHumanVerificationProvider verificationProvider =
        new RFWHumanVerificationProvider() {
          @Override
          public CompletionStage<RFWHumanVerificationResultVO> verify(
              String token,
              String remoteAddress) {
            throw new AssertionError("A verificação contextualizada deve ser utilizada.");
          }

          @Override
          public CompletionStage<RFWHumanVerificationResultVO> verify(
              RFWHumanVerificationRequestVO request) {
            verifiedTokens.add(request.token());
            return CompletableFuture.completedFuture(new RFWHumanVerificationResultVO(
                request.token() != null,
                request.token() == null ? null : "rinos.test",
                Instant.parse("2026-08-01T18:00:00Z"),
                List.of()));
          }
        };
    RegistrationCancellationFacade cancellationFacade =
        mock(RegistrationCancellationFacade.class);
    when(cancellationFacade.requestCancellation(any())).thenReturn(
        CompletableFuture.completedFuture(new RegistrationCancellationRequestResultVO(
            RegistrationCancellationRequestStatusEnum.VALIDATION_REJECTED,
            null,
            null,
            Map.of("identifier", "registration.error.email-invalid"))));
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(cancellationFacade);

    contextRunner
        .withBean(RFWHumanVerificationProvider.class, () -> verificationProvider)
        .withBean(RFWHumanVerificationRequirementProvider.class, () ->
            (operation, remoteAddress) -> true)
        .withBean(RFWRemoteAddressProvider.class, () ->
            ignored -> "203.0.113.10")
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
        .withPropertyValues(
            "rfw.authentication.turnstile.enabled=true",
            "rfw.authentication.turnstile.site-key=test-site")
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of());
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
            RFWAccessComponent component = hostFactory.create("indisponível");
            ui.add(component);
            component.open(new RFWAccessEntryRequestVO(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
                "original@example.com",
                null));
            TextField identifier = descendants(component, TextField.class).getFirst();
            identifier.setValue("invalid-email");
            RFWTurnstileComponent consumedTurnstile = descendants(
                component,
                RFWTurnstileComponent.class).getFirst();
            consumedTurnstile.acceptToken("single-use-token");

            descendants(component, Button.class).stream()
                .filter(button -> "Solicitar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            ArgumentCaptor<RegistrationCancellationRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(RegistrationCancellationRequestDTO.class);
            verify(cancellationFacade).requestCancellation(requestCaptor.capture());
            assertThat(requestCaptor.getValue().identifier()).isEqualTo("invalid-email");
            assertThat(component.getCurrentStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST);
            assertThat(component.getEntryRequest().identifier()).isEqualTo("invalid-email");
            assertThat(descendants(component, TextField.class).getFirst().getValue())
                .isEqualTo("invalid-email");
            assertThat(consumedTurnstile.consumeToken()).isNull();

            RFWTurnstileComponent renewedTurnstile = descendants(
                component,
                RFWTurnstileComponent.class).getFirst();
            assertThat(renewedTurnstile).isNotSameAs(consumedTurnstile);
            assertThat(renewedTurnstile.consumeToken()).isNull();

            descendants(component, Button.class).stream()
                .filter(button -> "Solicitar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            assertThat(verifiedTokens).containsExactly("single-use-token", null);
            verify(cancellationFacade, times(1)).requestCancellation(any());

            descendants(component, Button.class).stream()
                .filter(button -> "Voltar para entrar".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();
            assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.SIGN_IN);
            assertThat(component.getEntryRequest().identifier()).isNull();
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que ausência de resposta não é interpretada como solicitação aceita.
   */
  @Test
  void cancellationRequest_shouldRemainProcessingUntilProviderResponds() {
    CompletableFuture<RegistrationCancellationRequestResultVO> pendingRequest =
        new CompletableFuture<>();
    RegistrationCancellationFacade cancellationFacade =
        mock(RegistrationCancellationFacade.class);
    when(cancellationFacade.requestCancellation(any())).thenReturn(pendingRequest);
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(cancellationFacade);

    contextRunner
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of());
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            RFWAccessComponent component = hostFactory.create("indisponível");
            ui.add(component);
            component.open(new RFWAccessEntryRequestVO(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
                "person@example.com",
                null));

            descendants(component, Button.class).stream()
                .filter(button -> "Solicitar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            assertThat(component.isBusy()).isTrue();
            assertThat(component.getElement().getAttribute("aria-busy")).isEqualTo("true");
            assertThat(component.getCurrentStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST);
            assertThat(component.getCurrentChallenge()).isNull();

            pendingRequest.complete(new RegistrationCancellationRequestResultVO(
                RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
                "neutral-reference",
                Instant.parse("2026-08-02T18:00:00Z"),
                Map.of()));

            assertThat(component.isBusy()).isFalse();
            assertThat(component.getCurrentStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que indisponibilidade mantém a solicitação recuperável sem afirmar envio.
   */
  @Test
  void cancellationRequest_shouldRemainRecoverableWhenProviderIsUnavailable() {
    RegistrationCancellationFacade cancellationFacade =
        mock(RegistrationCancellationFacade.class);
    when(cancellationFacade.requestCancellation(any())).thenReturn(
        CompletableFuture.failedFuture(new IllegalStateException("provider unavailable")));
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(cancellationFacade);

    contextRunner
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of());
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            RFWAccessComponent component = hostFactory.create("indisponível");
            ui.add(component);
            component.open(new RFWAccessEntryRequestVO(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
                "person@example.com",
                null));

            descendants(component, Button.class).stream()
                .filter(button -> "Solicitar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            assertThat(component.isBusy()).isFalse();
            assertThat(component.getElement().getAttribute("aria-busy")).isEqualTo("false");
            assertThat(component.getCurrentStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST);
            assertThat(component.getCurrentChallenge()).isNull();
            assertThat(descendants(component, TextField.class).getFirst().getValue())
                .isEqualTo("person@example.com");
            assertThat(component.getFeedbackComponent().getElement().getText())
                .contains("A operação está temporariamente indisponível. Tente mais tarde.")
                .doesNotContain("instruções serão enviadas");
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que a prova deixa o campo antes da conclusão assíncrona e cruza somente o adapter real.
   */
  @Test
  void cancellationConfirmation_shouldClearProofBeforeHostProviderResponds() {
    CompletableFuture<RegistrationCancellationConfirmationResultVO> pendingConfirmation =
        new CompletableFuture<>();
    RegistrationCancellationFacade cancellationFacade =
        mock(RegistrationCancellationFacade.class);
    when(cancellationFacade.confirmCancellation(any())).thenReturn(pendingConfirmation);
    RFWRegistrationCancellationProviderAdapter cancellationProvider =
        new RFWRegistrationCancellationProviderAdapter(cancellationFacade);

    contextRunner
        .withBean(
            RFWRegistrationProvider.class,
            () -> mock(RFWRegistrationProvider.class))
        .withBean(
            RFWRegistrationCancellationProviderAdapter.class,
            () -> cancellationProvider)
        .run(context -> {
          assertThat(context).hasNotFailed();
          LegalDocumentFacade legalDocumentFacade = mock(LegalDocumentFacade.class);
          when(legalDocumentFacade.findCurrentDocuments()).thenReturn(List.of(
              legalReference("terms-v1", LegalDocumentTypeEnum.TERMS_OF_USE, true),
              legalReference("privacy-v1", LegalDocumentTypeEnum.PRIVACY_POLICY, true)));
          RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
              context.getBean(RFWAccessComponentFactory.class),
              legalDocumentFacade);
          VaadinService service = mock(VaadinService.class);
          when(service.getDeploymentConfiguration())
              .thenReturn(mock(DeploymentConfiguration.class));
          VaadinSession session = new TestVaadinSession(service);
          VaadinSession.setCurrent(session);
          session.lock();
          try {
            UI ui = new UI();
            ui.getInternals().setSession(session);
            UI.setCurrent(ui);
            RFWAccessComponent component = hostFactory.create("indisponível");
            ui.add(component);
            component.open(new RFWAccessEntryRequestVO(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION,
                "person@example.com",
                "opaque-proof"));
            List<TextField> fields = descendants(component, TextField.class);
            TextField proof = fields.get(1);

            assertThat(proof.isRequired()).isTrue();
            assertThat(proof.getElement().getAttribute("autocomplete"))
                .isEqualTo("one-time-code");

            descendants(component, Button.class).stream()
                .filter(button -> "Confirmar cancelamento".equals(button.getText()))
                .findFirst()
                .orElseThrow()
                .click();

            assertThat(proof.getValue()).isEmpty();
            assertThat(component.isBusy()).isTrue();
            ArgumentCaptor<RegistrationCancellationConfirmationDTO> request =
                ArgumentCaptor.forClass(RegistrationCancellationConfirmationDTO.class);
            verify(cancellationFacade).confirmCancellation(request.capture());
            assertThat(request.getValue().identifier()).isEqualTo("person@example.com");
            assertThat(request.getValue().proof()).isEqualTo("opaque-proof");
            assertThat(request.getValue().correlationId()).isNotNull();
            assertThat(descendants(component, Button.class))
                .noneMatch(button -> "Criar conta".equals(button.getText()));

            pendingConfirmation.complete(RegistrationCancellationConfirmationResultVO.of(
                RegistrationCancellationConfirmationStatusEnum.CANCELLED));
            assertThat(component.isBusy()).isFalse();
            assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.RESULT);
            assertThat(component.getResultOriginStep()).isEqualTo(
                RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
            assertThat(descendants(component, Button.class))
                .extracting(Button::getText)
                .containsExactly("Criar conta", "Voltar para entrar");
          } finally {
            session.unlock();
          }
        });
  }

  /**
   * Comprova que todos os estados públicos desta etapa possuem texto localizado na hospedeira.
   */
  @Test
  void context_shouldResolveAuthenticationAndRegistrationMessages() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      RFWTranslationService translations = context.getBean(RFWTranslationService.class);

      assertThat(List.of(
          "authentication.credentials.invalid",
          "authentication.sign-in.rate-limited",
          "authentication.temporarily-unavailable",
          "registration.activation.invalid-proof",
          "registration.activation.expired-proof",
          "registration.activation.used-proof",
          "registration.activation.registration-closed",
          "registration.cancellation.invalid-proof",
          "registration.cancellation.expired-proof",
          "registration.cancellation.completed",
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
          RFWAuthenticationPropertiesConfig.GoogleConfig googleConfig = context
              .getBean(RFWAuthenticationPropertiesConfig.class)
              .google();
          assertThat(googleConfig.clientId()).isEqualTo("test-client");
          assertThat(googleConfig.issuer()).isEqualTo("https://accounts.google.com");
          assertThat(googleConfig.timeout()).isEqualTo(java.time.Duration.ofMillis(750));
          assertThat(googleConfig.clockSkew()).isEqualTo(java.time.Duration.ofSeconds(45));
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
