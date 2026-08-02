package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.facade.LegalDocumentFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationStartFacadeImpl;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.repository.LegalConsentRepository;
import br.com.rinos.app.backend.module.identity.repository.LegalDocumentVersionRepository;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.OriginWindowRepository;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.repository.VerificationRepository;
import br.com.rinos.app.backend.module.identity.service.CommonPasswordService;
import br.com.rinos.app.backend.module.identity.service.EmailNormalizationService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.LegalDocumentIntegrityService;
import br.com.rinos.app.backend.module.identity.service.LocalCredentialService;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.service.PasswordPolicyService;
import br.com.rinos.app.backend.module.identity.service.PasswordPreparationService;
import br.com.rinos.app.backend.module.identity.service.PublicApplicationUriService;
import br.com.rinos.app.backend.module.identity.service.PwnedPasswordsService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCreationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.service.VerificationEmailDispatchService;
import br.com.rinos.app.backend.module.identity.service.VerificationService;
import br.com.rinos.app.backend.module.identity.service.VerificationTokenService;
import br.com.rinos.app.config.ApplicationPropertiesConfig;
import br.com.rinos.app.config.OriginPropertiesConfig;
import br.com.rinos.app.config.PasswordHashPropertiesConfig;
import br.com.rinos.app.config.PasswordSecurityConfig;
import br.com.rinos.app.config.RegistrationPropertiesConfig;
import br.com.rinos.app.config.VerificationPropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.com.rinos.app.ui.module.identity.component.RinosAccessComponentFactory;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.google.config.RFWGoogleAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.config.RFWAutoConfiguration;
import br.eng.rodrigogml.rfw.executioncontext.config.RFWExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.executioncontext.vaadin.config.RFWVaadinExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.i18n.config.RFWI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.i18n.vaadin.config.RFWVaadinI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailDispatcher;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import br.eng.rodrigogml.rfw.mail.EmailTemplateRenderer;
import br.eng.rodrigogml.rfw.mail.EmailTemplateResolver;
import br.eng.rodrigogml.rfw.session.vaadin.config.RFWVaadinSessionAutoConfiguration;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessEntryRequestVO;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;
import br.eng.rodrigogml.rfw.ui.access.config.RFWAccessUIAutoConfiguration;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;
import br.eng.rodrigogml.rfw.ui.theme.config.RFWThemeAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Prova a fronteira inteira do cadastro sem substituir nenhuma camada interna por mocks.
 *
 * <p>Somente integrações externas são simuladas: consulta de senha comprometida e transporte de
 * e-mail. UI RFW, adapter, facade, serviços, transação, repositories e MySQL permanecem reais.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@DisplayName("Roundtrip UI, facade, backend e MySQL do cadastro")
class RegistrationRoundtripIT {

  private static MySqlTestDatabase testDatabase;

  private DataSource dataSource;

  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  @BeforeEach
  void prepareSchema() {
    Assumptions.assumeTrue(testDatabase != null, "MySQL 9 ou Docker indisponível");
    dataSource = testDatabase.recreateSchema();
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"));
    populator.execute(dataSource);
  }

  @AfterEach
  void clearThreadContexts() {
    UI.setCurrent(null);
    VaadinSession.setCurrent(null);
    RequestContextHolder.resetRequestAttributes();
  }

  /**
   * Parte da API de submissão do componente e comprova todos os efeitos finais no schema real.
   */
  @Test
  void registration_shouldRoundtripFromRfwComponentToMySql_withoutEntityExposure() {
    contextRunner().run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(AopUtils.isAopProxy(context.getBean(RegistrationCreationService.class)))
          .isTrue();

      LegalDocumentVersionRepository documentRepository =
          context.getBean(LegalDocumentVersionRepository.class);
      LegalDocumentIntegrityService integrityService =
          context.getBean(LegalDocumentIntegrityService.class);
      TransactionTemplate transaction = new TransactionTemplate(
          context.getBean(PlatformTransactionManager.class));
      transaction.executeWithoutResult(status -> {
        documentRepository.save(legalDocument(
            LegalDocumentTypeEnum.TERMS_OF_USE,
            "terms-roundtrip",
            "Termos do teste integrado",
            integrityService));
        documentRepository.save(legalDocument(
            LegalDocumentTypeEnum.PRIVACY_POLICY,
            "privacy-roundtrip",
            "Privacidade do teste integrado",
            integrityService));
      });

      LegalDocumentFacade legalFacade = context.getBean(LegalDocumentFacade.class);
      List<LegalDocumentReferenceVO> legalDocuments = legalFacade.findCurrentDocuments();
      assertThat(legalDocuments).hasSize(2);
      RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
          context.getBean(RFWAccessComponentFactory.class),
          legalFacade);
      VaadinService vaadinService = mock(VaadinService.class);
      when(vaadinService.getDeploymentConfiguration())
          .thenReturn(mock(DeploymentConfiguration.class));
      TestVaadinSession session = new TestVaadinSession(vaadinService);
      VaadinSession.setCurrent(session);
      session.lock();
      try {
        UI ui = new UI();
        ui.getInternals().setSession(session);
        UI.setCurrent(ui);
        RFWAccessComponent component = hostFactory.create("indisponível");
        ui.add(component);
        component.open(new RFWAccessEntryRequestVO(
            RFWAccessStepEnum.REGISTRATION,
            null,
            null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
            new MockHttpServletRequest(),
            new MockHttpServletResponse()));

        component.submitRegistration(new RFWRegistrationRequestDTO(
            "roundtrip@example.com",
            "R0undtrip!Safe",
            legalDocuments.stream().map(LegalDocumentReferenceVO::reference).toList(),
            null));

        assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.ACTIVATION);
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.ACTIVATION_REQUIRED);
      } finally {
        session.unlock();
      }

      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LocalCredentialRepository credentialRepository =
          context.getBean(LocalCredentialRepository.class);
      LegalConsentRepository consentRepository = context.getBean(LegalConsentRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      OriginWindowRepository originRepository = context.getBean(OriginWindowRepository.class);

      assertThat(userRepository.findAll())
          .singleElement()
          .satisfies(user -> {
            assertThat(user.getEmail()).isEqualTo("roundtrip@example.com");
            assertThat(user.getStatus()).isEqualTo(UserStatusEnum.PENDING_VERIFICATION);
          });
      assertThat(registrationRepository.findAll())
          .singleElement()
          .satisfies(registration -> {
            assertThat(registration.getMethod()).isEqualTo(RegistrationMethodEnum.LOCAL);
            assertThat(registration.getStatus())
                .isEqualTo(RegistrationStatusEnum.PENDING_VERIFICATION);
          });
      assertThat(credentialRepository.findAll())
          .singleElement()
          .satisfies(credential -> {
            assertThat(credential.getPasswordHash()).startsWith("{argon2id}");
            assertThat(credential.getPasswordHash()).doesNotContain("R0undtrip!Safe");
          });
      assertThat(consentRepository.count()).isEqualTo(2);
      assertThat(verificationRepository.findAll())
          .singleElement()
          .satisfies(verification -> {
            assertThat(verification.getPurpose())
                .isEqualTo(VerificationPurposeEnum.REGISTRATION_EMAIL);
            assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.OPEN);
          });
      assertThat(eventRepository.findAll())
          .singleElement()
          .extracting(event -> event.getEventType())
          .isEqualTo(IdentityEventTypeEnum.REGISTRATION_STARTED);
      assertThat(originRepository.count()).isOne();
      assertThat(context.getBean(CapturingEmailDispatchService.class).lastMessage())
          .isNotNull()
          .satisfies(message -> {
            assertThat(message.toAddresses()).containsExactly("roundtrip@example.com");
            assertThat(message.body()).contains("/login?step=activation");
          });

      Stream.concat(
              Stream.of(RegistrationStartFacade.class.getMethods()),
              Stream.of(RFWRegistrationProviderAdapter.class.getMethods()))
          .flatMap(method -> Stream.concat(
              Stream.of(method.getGenericReturnType()),
              Stream.of(method.getGenericParameterTypes())))
          .map(type -> type.getTypeName())
          .forEach(typeName -> assertThat(typeName).doesNotContain(".entity."));
    });
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
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
        .withUserConfiguration(RoundtripConfig.class)
        .withPropertyValues(
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.hibernate.naming.physical-strategy="
                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
            "rfw.i18n.supported-language-locales=pt-BR",
            "rfw.i18n.default-language-locale=pt-BR",
            "rfw.i18n.default-format-locale=pt-BR",
            "rfw.i18n.default-zone-id=America/Sao_Paulo",
            "rfw.i18n.application-basenames=messages",
            "rfw.ui.theme.project-key=rinos",
            "rfw.ui.access.project-key=rinos",
            "rfw.ui.access.remember-me-enabled=false",
            "rfw.authentication.google.enabled=false",
            "rfw.authentication.turnstile.enabled=false")
        .withBean(DataSource.class, () -> dataSource);
  }

  private static LegalDocumentVersionEntity legalDocument(
      LegalDocumentTypeEnum type,
      String version,
      String content,
      LegalDocumentIntegrityService integrityService) {
    return new LegalDocumentVersionEntity(
        type,
        version,
        true,
        content,
        integrityService.hash(content),
        Instant.parse("2026-08-01T00:00:00Z"),
        null);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EntityScan(basePackageClasses = UserEntity.class)
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  static class RoundtripConfig {

    @Bean
    EmailNormalizationService emailNormalizationService() {
      return new EmailNormalizationService();
    }

    @Bean
    IdentityService identityService(
        UserRepository users,
        RegistrationRepository registrations,
        EmailNormalizationService normalization) {
      return new IdentityService(users, registrations, normalization);
    }

    @Bean
    LegalDocumentIntegrityService legalDocumentIntegrityService() {
      return new LegalDocumentIntegrityService();
    }

    @Bean
    LegalConsentService legalConsentService(
        LegalDocumentVersionRepository documents,
        LegalConsentRepository consents) {
      return new LegalConsentService(documents, consents);
    }

    @Bean
    LegalDocumentFacade legalDocumentFacade(
        LegalConsentService consents,
        LegalDocumentVersionRepository documents,
        LegalDocumentIntegrityService integrity) {
      return new LegalDocumentFacadeImpl(consents, documents, integrity);
    }

    @Bean
    CommonPasswordService commonPasswordService() {
      return new CommonPasswordService();
    }

    @Bean
    PwnedPasswordsService pwnedPasswordsService() {
      PwnedPasswordsService service = mock(PwnedPasswordsService.class);
      when(service.check(any(char[].class))).thenReturn(PwnedPasswordStatusEnum.SAFE);
      return service;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return new PasswordSecurityConfig().rinosPasswordEncoder(
          new PasswordHashPropertiesConfig(19_456, 2, 1, 16, 32));
    }

    @Bean
    PasswordPreparationService passwordPreparationService(
        CommonPasswordService commonPasswords,
        PwnedPasswordsService pwnedPasswords,
        PasswordEncoder encoder) {
      return new PasswordPreparationService(
          new PasswordPolicyService(commonPasswords, pwnedPasswords),
          encoder);
    }

    @Bean
    LocalCredentialService localCredentialService(LocalCredentialRepository credentials) {
      return new LocalCredentialService(credentials);
    }

    @Bean
    VerificationService verificationService(
        VerificationRepository verifications,
        RegistrationRepository registrations) {
      return new VerificationService(
          verifications,
          registrations,
          new VerificationTokenService(),
          new VerificationPropertiesConfig(Duration.ofHours(24)));
    }

    @Bean
    OriginLimitService originLimitService(OriginWindowRepository origins) {
      return new OriginLimitService(
          origins,
          new OriginPropertiesConfig(20, 20, Duration.ofHours(24), Duration.ofDays(30)));
    }

    @Bean
    IdentityAuditService identityAuditService(IdentityEventRepository events) {
      return new IdentityAuditService(events);
    }

    @Bean
    PublicApplicationUriService publicApplicationUriService() {
      return new PublicApplicationUriService(
          new ApplicationPropertiesConfig(URI.create("http://localhost:7070")));
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    CapturingEmailDispatchService emailDispatchService() {
      return new CapturingEmailDispatchService();
    }

    @Bean
    VerificationEmailDispatchService verificationEmailDispatchService(
        CapturingEmailDispatchService emailDispatchService,
        MeterRegistry meterRegistry) {
      return new VerificationEmailDispatchService(emailDispatchService, meterRegistry);
    }

    @Bean
    RegistrationPropertiesConfig registrationPropertiesConfig() {
      return new RegistrationPropertiesConfig(
          Duration.ofDays(15),
          3,
          Duration.ofMinutes(15),
          3,
          Duration.ofMinutes(15));
    }

    @Bean
    RegistrationCreationService registrationCreationService(
        IdentityService identities,
        LocalCredentialService credentials,
        LegalConsentService consents,
        VerificationService verifications,
        OriginLimitService origins,
        IdentityAuditService audit,
        PublicApplicationUriService uriService,
        VerificationEmailDispatchService dispatch,
        RegistrationPropertiesConfig properties) {
      return new RegistrationCreationService(
          identities,
          credentials,
          consents,
          verifications,
          origins,
          audit,
          uriService,
          dispatch,
          properties);
    }

    @Bean
    RegistrationObservabilityService registrationObservabilityService(
        MeterRegistry meterRegistry) {
      return new RegistrationObservabilityService(meterRegistry);
    }

    @Bean
    RegistrationStartFacade registrationStartFacade(
        IdentityService identities,
        PasswordPreparationService passwords,
        LegalConsentService consents,
        RegistrationCreationService creation,
        IdentityAuditService audit,
        RegistrationObservabilityService observability) {
      return new RegistrationStartFacadeImpl(
          identities,
          passwords,
          consents,
          new OriginAddressService(),
          creation,
          audit,
          observability);
    }

    @Bean
    RegistrationResendFacade registrationResendFacade() {
      return mock(RegistrationResendFacade.class);
    }

    @Bean
    RegistrationActivationFacade registrationActivationFacade() {
      return mock(RegistrationActivationFacade.class);
    }

    @Bean
    RFWRemoteAddressProvider remoteAddressProvider() {
      return ignored -> "203.0.113.10";
    }

    @Bean
    RFWRegistrationProviderAdapter registrationProvider(
        RegistrationStartFacade registration,
        RegistrationResendFacade resend,
        RegistrationActivationFacade activation,
        RFWRemoteAddressProvider remoteAddressProvider) {
      return new RFWRegistrationProviderAdapter(
          registration,
          resend,
          activation,
          remoteAddressProvider);
    }
  }

  /** Simula somente o transporte SMTP e preserva a mensagem final para a asserção. */
  static final class CapturingEmailDispatchService extends EmailDispatchService {

    private final AtomicReference<EmailMessage> lastMessage = new AtomicReference<>();

    CapturingEmailDispatchService() {
      super(
          mock(EmailTemplateResolver.class),
          mock(EmailTemplateRenderer.class),
          mock(EmailDispatcher.class));
    }

    @Override
    public EmailMessage createMessage(
        String templateName,
        Locale locale,
        String fromAddress,
        List<String> toAddresses,
        List<String> ccAddresses,
        List<String> bccAddresses,
        String replyToAddress,
        Object... params) {
      return EmailMessage.builder()
          .subject(templateName)
          .body(String.valueOf(params[0]), false)
          .buildFromLists(toAddresses, ccAddresses, bccAddresses);
    }

    @Override
    public void dispatch(EmailMessage message) {
      lastMessage.set(message);
    }

    EmailMessage lastMessage() {
      return lastMessage.get();
    }
  }

  /** Fornece o lock mínimo exigido pelo componente Vaadin anexado. */
  static final class TestVaadinSession extends VaadinSession {

    private static final long serialVersionUID = 1L;

    private final ReentrantLock lock = new ReentrantLock();

    TestVaadinSession(VaadinService service) {
      super(service);
    }

    @Override
    public Lock getLockInstance() {
      return lock;
    }
  }
}
