package br.com.rinos.app.ui.module.identity.view;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;

import br.com.rinos.app.api.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.vo.LegalDocumentContentVO;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWActivationRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.platform.authentication.provider.RFWRegistrationProvider;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAccessChallengeVO;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Inicializa somente a superfície Vaadin do cadastro com contratos determinísticos em memória.
 *
 * <p>O harness comprova a integração da view com os componentes públicos do RFW sem simular
 * persistência. O roundtrip com MySQL pertence ao gate integrado da fase 7.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    SecurityAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    com.vaadin.flow.spring.SpringSecurityAutoConfiguration.class
})
@Import({
    RinosAccessComponentFactory.class,
    RegistrationUiTestApplication.FixtureConfig.class
})
@StyleSheet("context://rfw-platform/styles.css")
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
        "rfw.platform.i18n.enabled", "true",
        "rfw.platform.i18n.default-language-locale", "pt-BR",
        "rfw.platform.i18n.default-format-locale", "pt-BR",
        "rfw.platform.i18n.application-basenames", "messages",
        "rfw.platform.ui.access.language-switcher-enabled", "false",
        "rfw.platform.ui.access.remember-me-enabled", "false"));
    application.run();
  }

  /**
   * Fornece somente os contratos necessários para renderizar e concluir a jornada controlada.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfig {

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

    private static RFWAccessChallengeVO activationChallenge() {
      return new RFWAccessChallengeVO(
          "activation-flow",
          RFWAuthenticationMethodEnum.EMAIL_CODE,
          "p***@example.com",
          Instant.parse("2026-08-01T15:00:00Z"),
          Set.of(RFWAuthenticationMethodEnum.EMAIL_CODE));
    }
  }
}
