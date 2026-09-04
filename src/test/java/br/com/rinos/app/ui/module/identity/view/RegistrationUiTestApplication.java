package br.com.rinos.app.ui.module.identity.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.spring.annotation.EnableVaadin;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.facade.GoogleAuthenticationFacade;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.config.SecurityConfig;
import br.com.rinos.app.ui.config.RFWExternalIdentityResolverAdapter;
import br.com.rinos.app.ui.config.RFWExternalRegistrationProviderAdapter;
import br.com.rinos.app.ui.config.RFWAuthenticationOutcomeAdapter;
import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.dto.RFWActivationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalIdentityRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationCancellationConfirmationDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationCancellationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityResolver;
import br.eng.rodrigogml.rfw.authentication.provider.RFWRegistrationProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWRegistrationCancellationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;

/**
 * Inicializa a superfície Vaadin do cadastro com segurança real e fronteiras externas simuladas.
 *
 * <p>O harness comprova a integração da view com os componentes públicos do RFW, os adapters
 * do Rinos e a sessão Spring Security. Somente persistência, SDK e validação criptográfica
 * remota do Google são substituídos por contratos determinísticos. O roundtrip com MySQL
 * pertence ao gate integrado da fase 7.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@Import({
    RinosAccessComponentFactory.class,
    RFWAuthenticationOutcomeAdapter.class,
    RFWExternalIdentityResolverAdapter.class,
    RFWExternalRegistrationProviderAdapter.class,
    SecurityConfig.class,
    RegistrationUiTestApplication.FixtureConfig.class
})
@EnableVaadin("br.com.rinos.app.ui")
@StyleSheet("context://rfw/styles.css")
public class RegistrationUiTestApplication implements AppShellConfigurator {

  /**
   * Executa o harness visual com a porta exclusiva definida no {@code application.properties}.
   *
   * @param arguments ignorados para impedir configuração acidental por linha de comando
   */
  public static void main(String[] arguments) {
    SpringApplication application = new SpringApplication(RegistrationUiTestApplication.class);
    application.setDefaultProperties(Map.of(
        "spring.main.banner-mode", "off",
        "vaadin.launch-browser", "false",
        "rfw.i18n.enabled", "true",
        "rfw.i18n.default-language-locale", "pt-BR",
        "rfw.i18n.default-format-locale", "pt-BR",
        "rfw.i18n.application-basenames", "messages",
        "rfw.ui.access.language-switcher-enabled", "false",
        "rfw.ui.access.remember-me-enabled", "false"));
    application.run();
  }

  /**
   * Fornece somente os contratos necessários para renderizar e concluir a jornada controlada.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfig {

    /**
     * Fornece a configuração pública do Google sem contornar a origem exclusiva de properties da aplicação.
     *
     * @return configuração tipada restrita ao contexto de teste
     */
    @Bean
    @Primary
    RFWAuthenticationPropertiesConfig testAuthenticationProperties() {
      return new RFWAuthenticationPropertiesConfig(
          new RFWAuthenticationPropertiesConfig.GoogleConfig(
              false,
              "test-google-client",
              "https://accounts.google.com"),
          null,
          null,
          null);
    }

    /**
     * Impede que o harness sem persistência execute a migração global configurada para a aplicação real.
     *
     * @return inicializador deliberadamente vazio
     */
    @Bean("databaseUpdateStartupInitializer")
    SmartInitializingSingleton databaseUpdateStartupInitializer() {
      return () -> {
        // O contrato de migração é exercitado pelos testes de integração de banco.
      };
    }

    /**
     * Publica a fotografia jurídica mínima e obrigatória usada pela tela de cadastro.
     *
     * @return facade determinística sem persistência
     */
    @Bean
    LegalDocumentFacade legalDocumentFacade() {
      return new LegalDocumentFacade() {
        @Override
        public List<LegalDocumentReferenceVO> findCurrentDocuments() {
          return List.of(
              new LegalDocumentReferenceVO(
                  "terms-v1",
                  LegalDocumentTypeEnum.TERMS_OF_USE,
                  "1.0.0",
                  true),
              new LegalDocumentReferenceVO(
                  "privacy-v1",
                  LegalDocumentTypeEnum.PRIVACY_POLICY,
                  "1.0.0",
                  true));
        }

        @Override
        public Optional<LegalDocumentContentVO> findPublishedDocument(String reference) {
          return Optional.empty();
        }
      };
    }

    /**
     * Expõe a capability de cadastro e conduz a submissão nominal para a etapa de ativação.
     *
     * @return provider controlado da superfície
     */
    @Bean
    RFWRegistrationProvider registrationProvider() {
      return new RFWRegistrationProvider() {
        @Override
        public CompletionStage<RFWAuthenticationOutcomeVO> register(
            RFWRegistrationRequestDTO request) {
          return CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.activationRequired(
                  "registration.email-sent",
                  activationChallenge()));
        }

        @Override
        public CompletionStage<RFWAuthenticationOutcomeVO> activate(
            RFWActivationRequestDTO request) {
          return CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.completed("registration.activation-completed"));
        }

        @Override
        public CompletionStage<RFWAuthenticationOutcomeVO> resendActivation(String identifier) {
          return CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.activationRequired(
                  "registration.activation-resent",
                  activationChallenge()));
        }
      };
    }

    /**
     * Expõe o fluxo de cancelamento com rejeição por campo e continuação determinísticas.
     *
     * @return provider controlado para a jornada acessível em navegador
     */
    @Bean
    RFWRegistrationCancellationProvider registrationCancellationProvider() {
      return new RFWRegistrationCancellationProvider() {
        @Override
        public CompletionStage<RFWAuthenticationOutcomeVO> requestCancellation(
            RFWRegistrationCancellationRequestDTO request) {
          if (request.identifier() == null || request.identifier().isBlank()) {
            return CompletableFuture.completedFuture(RFWAuthenticationOutcomeVO.rejected(
                new RFWAccessErrorVO(
                    "registration.validation-rejected",
                    List.of(),
                    Map.of("identifier", "registration.error.email-invalid"),
                    null)));
          }
          return CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.registrationCancellationRequired(
                  cancellationChallenge()));
        }

        @Override
        public CompletionStage<RFWAuthenticationOutcomeVO> confirmCancellation(
            RFWRegistrationCancellationConfirmationDTO request) {
          return CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.completed("registration.cancellation.completed"));
        }
      };
    }

    /**
     * Simula somente a validação externa da credencial e preserva o resolvedor real do Rinos.
     *
     * @param resolver adapter real que converte a identidade validada em decisão de cadastro
     * @return provider Google determinístico do harness
     */
    @Bean
    RFWExternalIdentityProvider googleIdentityProvider(
        RFWExternalIdentityResolver resolver) {
      return new SimulatedGoogleIdentityProvider(resolver);
    }

    /**
     * Emite a continuação opaca depois que o provider simulado comprova a identidade externa.
     *
     * @return facade determinística consumida pelo adapter real da interface
     */
    @Bean
    GoogleIdentityResolutionFacade googleIdentityResolutionFacade() {
      return request -> CompletableFuture.completedFuture(
          GoogleIdentityResolutionResultVO.continuation(
              "test-google-success",
              request.providerId(),
              request.email(),
              Instant.parse("2099-08-02T15:00:00Z")));
    }

    /**
     * Mantém o harness de cadastro no caminho de identidade estável ainda não vinculada.
     *
     * @return decisão interna que permite continuar o cadastro externo
     */
    @Bean
    GoogleAuthenticationFacade googleAuthenticationFacade() {
      return request -> CompletableFuture.completedFuture(
          br.com.rinos.app.api.vo.GoogleAuthenticationResultVO.identityNotFound());
    }

    /**
     * Conclui nominalmente a continuação Google e preserva o cenário de rejeição da rota direta.
     *
     * @return facade determinística consumida pelo adapter real da interface
     */
    @Bean
    ExternalRegistrationFacade externalRegistrationFacade() {
      return request -> {
        if ("test-only-external-registration".equals(request.registrationReference())) {
          return CompletableFuture.completedFuture(
              ExternalRegistrationCompletionResultVO.validationRejected(Map.of(
                  "acceptedLegalDocumentIds",
                  "registration.error.legal-documents")));
        }
        if (!"test-google-success".equals(request.registrationReference())
            || !request.acceptedLegalDocumentIds().containsAll(
                List.of("terms-v1", "privacy-v1"))) {
          return CompletableFuture.completedFuture(
              ExternalRegistrationCompletionResultVO.validationRejected(Map.of(
                  "acceptedLegalDocumentIds",
                  "registration.error.legal-documents")));
        }
        return CompletableFuture.completedFuture(
            ExternalRegistrationCompletionResultVO.authenticated(
                new br.com.rinos.app.api.vo.RegistrationAuthenticationContinuationVO(
                    new RinosUserPrincipalVO(41L, "verified@example.com"),
                    new br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO(
                        "registration-ui-continuation",
                        br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum
                            .REGISTRATION_ACTIVATION))));
      };
    }

    private static RFWAccessChallengeVO activationChallenge() {
      return new RFWAccessChallengeVO(
          "activation-flow",
          RFWAuthenticationMethodEnum.EMAIL_CODE,
          "p***@example.com",
          Instant.parse("2026-08-01T15:00:00Z"),
          Set.of(RFWAuthenticationMethodEnum.EMAIL_CODE));
    }

    private static RFWAccessChallengeVO cancellationChallenge() {
      return new RFWAccessChallengeVO(
          "cancellation-flow",
          RFWAuthenticationMethodEnum.EMAIL_CODE,
          null,
          Instant.parse("2026-08-01T15:00:00Z"),
          Set.of(RFWAuthenticationMethodEnum.EMAIL_CODE));
    }
  }

  /**
   * Substitui apenas a verificação criptográfica remota do Google no harness visual.
   *
   * <p>A identidade mínima produzida segue pelo resolvedor e pelos adapters reais do Rinos.</p>
   */
  private static final class SimulatedGoogleIdentityProvider
      implements RFWExternalIdentityProvider {

    private static final String PROVIDER_ID = "google";
    private static final String CREDENTIAL = "test-google-credential";

    private final RFWExternalIdentityResolver resolver;

    private SimulatedGoogleIdentityProvider(RFWExternalIdentityResolver resolver) {
      this.resolver = resolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProviderId() {
      return PROVIDER_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<Optional<RFWVerifiedExternalIdentityVO>> verify(
        RFWExternalIdentityRequestDTO request) {
      if (request == null
          || !PROVIDER_ID.equals(request.providerId())
          || !CREDENTIAL.equals(request.credential())
          || request.nonce() == null
          || request.nonce().isBlank()) {
        return CompletableFuture.completedFuture(Optional.empty());
      }
      return CompletableFuture.completedFuture(Optional.of(
          new RFWVerifiedExternalIdentityVO(
              PROVIDER_ID,
              "test-google-subject",
              "verified@example.com",
              true,
              Map.of("iss", "https://accounts.google.com"))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletionStage<RFWAuthenticationOutcomeVO> authenticate(
        RFWExternalIdentityRequestDTO request) {
      return verify(request).thenCompose(identity -> identity
          .map(resolver::resolve)
          .orElseGet(() -> CompletableFuture.completedFuture(
              RFWAuthenticationOutcomeVO.rejected(RFWAccessErrorVO.of(
                  "ui.access.error.externalIdentityRejected")))));
    }
  }
}
