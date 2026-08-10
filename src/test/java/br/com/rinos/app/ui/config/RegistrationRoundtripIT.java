package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.facade.GoogleAuthenticationFacade;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.facade.LegalDocumentFacade;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.LegalDocumentReferenceVO;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.PwnedPasswordStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.facade.ExternalRegistrationFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.GoogleIdentityResolutionFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.LegalDocumentFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationActivationFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationCancellationFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationResendFacadeImpl;
import br.com.rinos.app.backend.module.identity.facade.RegistrationStartFacadeImpl;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;
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
import br.com.rinos.app.backend.module.identity.service.EmailPrivacyService;
import br.com.rinos.app.backend.module.identity.service.ExternalRegistrationCompletionService;
import br.com.rinos.app.backend.module.identity.service.ExternalIdentityService;
import br.com.rinos.app.backend.module.identity.service.GoogleIdentityResolutionService;
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
import br.com.rinos.app.backend.module.identity.service.RegistrationActivationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCancellationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationLifecycleService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationResendService;
import br.com.rinos.app.backend.module.identity.service.UserLifecycleService;
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
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWActivationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWActivationConsentRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationCancellationConfirmationDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationCancellationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.google.config.RFWGoogleAuthenticationAutoConfiguration;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityResolver;
import br.eng.rodrigogml.rfw.authentication.service.RFWAuthenticationSessionService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;
import br.eng.rodrigogml.rfw.config.RFWAutoConfiguration;
import br.eng.rodrigogml.rfw.executioncontext.config.RFWExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.executioncontext.vaadin.config.RFWVaadinExecutionContextAutoConfiguration;
import br.eng.rodrigogml.rfw.i18n.config.RFWI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.i18n.vaadin.config.RFWVaadinI18nAutoConfiguration;
import br.eng.rodrigogml.rfw.logging.RFWLogger;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;
import br.eng.rodrigogml.rfw.mail.ClasspathEmailTemplateResolver;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailDispatcher;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import br.eng.rodrigogml.rfw.mail.EmailTemplateRenderer;
import br.eng.rodrigogml.rfw.mail.EmailTemplateResolver;
import br.eng.rodrigogml.rfw.mail.PositionalEmailTemplateRenderer;
import br.eng.rodrigogml.rfw.mail.SmtpEmailDispatcher;
import br.eng.rodrigogml.rfw.mail.config.EmailTemplatePropertiesConfig;
import br.eng.rodrigogml.rfw.session.vaadin.config.RFWVaadinSessionAutoConfiguration;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponent;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessComponentFactory;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessEntryRequestVO;
import br.eng.rodrigogml.rfw.ui.access.RFWAccessStepEnum;
import br.eng.rodrigogml.rfw.ui.access.RFWLegalDocumentVO;
import br.eng.rodrigogml.rfw.ui.access.config.RFWAccessComponentConfig;
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

  private static final int SMTP_GATE_SAMPLE_SIZE = 100;
  private static final String SMTP_ATTEMPT_METRIC =
      "rinos.registration.verification.smtp.attempts";
  private static final String SMTP_DURATION_METRIC =
      "rinos.registration.verification.smtp.duration";

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

  /**
   * Mede cem aceitações reais do dispatcher RFW contra um servidor SMTP local controlado.
   *
   * <p>Cada execução abre uma transação externa, registra o instante no primeiro callback
   * pós-commit e aguarda a conclusão SMTP fora da função transacional. A duração registrada é um
   * limite superior conservador entre o commit efetivo e a aceitação, pois termina apenas quando o
   * callback transacional retorna depois do aceite.</p>
   */
  @Test
  @EnabledIfSystemProperty(named = "rinos.smtp.gate.enabled", matches = "true")
  void smtpGate_shouldMeasureOneHundredAcceptancesFromTheirCommits() {
    GreenMail smtp = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
    smtp.start();
    SimpleMeterRegistry smtpMetrics = new SimpleMeterRegistry();
    try {
      VerificationEmailDispatchService smtpDispatch = new VerificationEmailDispatchService(
          localSmtpEmailDispatchService(smtp.getSmtp().getPort()),
          smtpMetrics);
      contextRunner()
          .withUserConfiguration(SmtpGateOriginConfig.class)
          .withBean(
              "smtpGateVerificationEmailDispatchService",
              VerificationEmailDispatchService.class,
              () -> smtpDispatch,
              definition -> definition.setPrimary(true))
          .run(context -> {
            assertThat(context).hasNotFailed();
            List<String> acceptedDocumentIds = seedLegalDocuments(context, "smtp-gate").stream()
                .map(LegalDocumentReferenceVO::reference)
                .toList();
            RegistrationStartFacade registration = context.getBean(
                RegistrationStartFacade.class);
            TransactionTemplate transaction = new TransactionTemplate(
                context.getBean(PlatformTransactionManager.class));
            List<SmtpGateSample> samples = new ArrayList<>(SMTP_GATE_SAMPLE_SIZE);

            for (int sequence = 1; sequence <= SMTP_GATE_SAMPLE_SIZE; sequence++) {
              UUID correlationId = UUID.randomUUID();
              AtomicLong committedAtNanos = new AtomicLong(-1L);
              RegistrationStartRequestDTO request = new RegistrationStartRequestDTO(
                  "smtp-gate-%03d@example.test".formatted(sequence),
                  "SmtpGate!42".toCharArray(),
                  acceptedDocumentIds,
                  "198.51.100.40",
                  Locale.forLanguageTag("pt-BR"),
                  correlationId);

              CompletionStage<RegistrationStartResultVO> pending = transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        committedAtNanos.set(System.nanoTime());
                      }
                    });
                return registration.start(request);
              });
              assertThat(pending).isNotNull();
              RegistrationStartResultVO result = pending.toCompletableFuture().join();
              long commitNanos = committedAtNanos.get();
              assertThat(commitNanos).isPositive();
              Duration commitToAcceptanceUpperBound = Duration.ofNanos(
                  System.nanoTime() - commitNanos);
              assertThat(result.status()).isEqualTo(RegistrationStartStatusEnum.EMAIL_SENT);
              samples.add(new SmtpGateSample(
                  sequence,
                  correlationId,
                  commitToAcceptanceUpperBound));
            }

            assertThat(smtp.waitForIncomingEmail(5_000, SMTP_GATE_SAMPLE_SIZE)).isTrue();
            assertThat(smtp.getReceivedMessages()).hasSize(SMTP_GATE_SAMPLE_SIZE);
            assertThat(context.getBean(UserRepository.class).count())
                .isEqualTo(SMTP_GATE_SAMPLE_SIZE);
            assertThat(context.getBean(RegistrationRepository.class).count())
                .isEqualTo(SMTP_GATE_SAMPLE_SIZE);
            assertThat(context.getBean(OriginWindowRepository.class).findAll())
                .singleElement()
                .satisfies(origin -> assertThat(origin.getEventCount())
                    .isEqualTo(SMTP_GATE_SAMPLE_SIZE));
            assertThat(smtpMetrics.find(SMTP_ATTEMPT_METRIC)
                .tag("result", "accepted")
                .counter())
                .isNotNull()
                .satisfies(counter -> assertThat(counter.count())
                    .isEqualTo(SMTP_GATE_SAMPLE_SIZE));
            assertThat(smtpMetrics.find(SMTP_DURATION_METRIC)
                .tag("result", "accepted")
                .timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count())
                    .isEqualTo(SMTP_GATE_SAMPLE_SIZE));

            writeSmtpGateReport(samples, smtpMetrics);
          });
    } finally {
      smtp.stop();
      smtpMetrics.close();
    }
  }

  /**
   * Produz validação, indisponibilidade externa, e-mail existente e limitação usando a facade real.
   */
  @Test
  void registrationStates_shouldBeRenderedFromRealFacadeDecisions() {
    contextRunner().run(context -> {
      assertThat(context).hasNotFailed();
      List<LegalDocumentReferenceVO> documents = seedLegalDocuments(context, "states");
      List<String> acceptedIds = documents.stream()
          .map(LegalDocumentReferenceVO::reference)
          .toList();

      withAttachedComponent(context, component -> {
        component.open(new RFWAccessEntryRequestVO(
            RFWAccessStepEnum.REGISTRATION,
            null,
            null));
        assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.REGISTRATION);

        component.submitRegistration(new RFWRegistrationRequestDTO(
            "invalid@example.com",
            "fraca",
            acceptedIds,
            null));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);
        assertThat(context.getBean(UserRepository.class).count()).isZero();

        PwnedPasswordsService compromisedBoundary =
            context.getBean(PwnedPasswordsService.class);
        when(compromisedBoundary.check(any(char[].class)))
            .thenReturn(PwnedPasswordStatusEnum.UNAVAILABLE);
        component.submitRegistration(new RFWRegistrationRequestDTO(
            "hibp-unavailable@example.com",
            "R0undtrip!Safe",
            acceptedIds,
            null));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);
        assertThat(context.getBean(UserRepository.class).count()).isZero();
        when(compromisedBoundary.check(any(char[].class)))
            .thenReturn(PwnedPasswordStatusEnum.SAFE);

        TransactionTemplate transaction = transaction(context);
        transaction.executeWithoutResult(status -> context.getBean(UserRepository.class)
            .saveAndFlush(new UserEntity(
                "existing@example.com",
                "existing@example.com",
                UserStatusEnum.ACTIVE)));
        component.submitRegistration(new RFWRegistrationRequestDTO(
            "existing@example.com",
            "R0undtrip!Safe",
            acceptedIds,
            null));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);

        OriginLimitService origins = context.getBean(OriginLimitService.class);
        OriginAddressService addresses = new OriginAddressService();
        for (int attempt = 0; attempt < 20; attempt++) {
          origins.reserveNewRegistration(
              addresses.normalize("203.0.113.10"),
              br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum
                  .USER_REGISTRATION);
        }
        component.submitRegistration(new RFWRegistrationRequestDTO(
            "rate-limited@example.com",
            "R0undtrip!Safe",
            acceptedIds,
            null));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.RATE_LIMITED);
        assertThat(context.getBean(UserRepository.class)
            .findByNormalizedEmail("rate-limited@example.com")).isEmpty();
      });
    });
  }

  /**
   * Usa provas realmente persistidas para rejeição, reenvio e ativação idempotente na UI.
   */
  @Test
  void activationStates_shouldUsePersistedProofsAndLifecycle() {
    contextRunner().run(context -> {
      assertThat(context).hasNotFailed();
      List<String> acceptedIds = seedLegalDocuments(context, "activation").stream()
          .map(LegalDocumentReferenceVO::reference)
          .toList();
      CapturingEmailDispatchService email =
          context.getBean(CapturingEmailDispatchService.class);

      withAttachedComponent(context, component -> {
        component.open(new RFWAccessEntryRequestVO(
            RFWAccessStepEnum.REGISTRATION,
            null,
            null));
        component.submitRegistration(new RFWRegistrationRequestDTO(
            "activation-states@example.com",
            "R0undtrip!Safe",
            acceptedIds,
            null));
        String originalProof = email.lastManualCode();
        assertThat(originalProof).isNotBlank().isNotEqualTo("null");

        component.submitActivation(new RFWActivationRequestDTO(
            "activation-states@example.com",
            "invalid-proof"));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);
        assertThat(context.getBean(UserRepository.class).findAll().getFirst().getStatus())
            .isEqualTo(UserStatusEnum.PENDING_VERIFICATION);

        component.resendActivation("activation-states@example.com");
        String reissuedProof = email.lastManualCode();
        assertThat(reissuedProof).isNotBlank().isNotEqualTo(originalProof);
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.ACTIVATION_REQUIRED);
        assertThat(context.getBean(VerificationRepository.class).findAll())
            .extracting(verification -> verification.getStatus())
            .containsExactlyInAnyOrder(
                VerificationStatusEnum.INVALIDATED,
                VerificationStatusEnum.OPEN);

        component.submitActivation(new RFWActivationRequestDTO(
            "activation-states@example.com",
            reissuedProof));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.COMPLETED);
        assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.RESULT);
      });

      assertThat(context.getBean(UserRepository.class).findAll().getFirst().getStatus())
          .isEqualTo(UserStatusEnum.ACTIVE);
      assertThat(context.getBean(RegistrationRepository.class).findAll().getFirst().getStatus())
          .isEqualTo(RegistrationStatusEnum.ACTIVE);
      assertThat(context.getBean(VerificationRepository.class).findAll())
          .extracting(verification -> verification.getStatus())
          .containsOnly(VerificationStatusEnum.USED, VerificationStatusEnum.INVALIDATED);
    });
  }

  /**
   * Produz a continuação legal a partir de versões substituídas depois da criação da pendência.
   */
  @Test
  void activationPartialStale_shouldRenderAndCompleteWithCurrentDocuments() {
    contextRunner().run(context -> {
      assertThat(context).hasNotFailed();
      List<String> acceptedIds = seedLegalDocuments(context, "old").stream()
          .map(LegalDocumentReferenceVO::reference)
          .toList();
      CapturingEmailDispatchService email =
          context.getBean(CapturingEmailDispatchService.class);

      withAttachedComponent(context, component -> {
        component.open(new RFWAccessEntryRequestVO(
            RFWAccessStepEnum.REGISTRATION,
            null,
            null));
        component.submitRegistration(new RFWRegistrationRequestDTO(
            "legal-stale@example.com",
            "R0undtrip!Safe",
            acceptedIds,
            null));
        String proof = email.lastManualCode();

        Instant changedAt = Instant.now().minusMillis(1);
        new JdbcTemplate(dataSource).update(
            "UPDATE identity_legalDocumentVersion SET retiredAt = ? WHERE retiredAt IS NULL",
            java.sql.Timestamp.from(changedAt));
        LegalDocumentIntegrityService integrity =
            context.getBean(LegalDocumentIntegrityService.class);
        TransactionTemplate transaction = transaction(context);
        transaction.executeWithoutResult(status -> {
          LegalDocumentVersionRepository repository =
              context.getBean(LegalDocumentVersionRepository.class);
          repository.save(new LegalDocumentVersionEntity(
              LegalDocumentTypeEnum.TERMS_OF_USE,
              "terms-current",
              true,
              "Termos atuais",
              integrity.hash("Termos atuais"),
              changedAt,
              null));
          repository.save(new LegalDocumentVersionEntity(
              LegalDocumentTypeEnum.PRIVACY_POLICY,
              "privacy-current",
              true,
              "Privacidade atual",
              integrity.hash("Privacidade atual"),
              changedAt,
              null));
        });

        component.submitActivation(new RFWActivationRequestDTO(
            "legal-stale@example.com",
            proof));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.ACTIVATION_CONSENT_REQUIRED);
        assertThat(component.getCurrentStep())
            .isEqualTo(RFWAccessStepEnum.ACTIVATION_CONSENT);
        assertThat(component.getCurrentActivationConsent().legalDocumentIds())
            .hasSize(2)
            .doesNotContainAnyElementsOf(acceptedIds);
        assertThat(component.getLegalDocuments())
            .extracting(RFWLegalDocumentVO::id)
            .containsExactlyInAnyOrderElementsOf(
                component.getCurrentActivationConsent().legalDocumentIds());

        component.submitActivationConsent(new RFWActivationConsentRequestDTO(
            component.getCurrentActivationConsent().activationReference(),
            component.getCurrentActivationConsent().legalDocumentIds().stream().toList()));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.COMPLETED);
      });

      assertThat(context.getBean(UserRepository.class).findAll().getFirst().getStatus())
          .isEqualTo(UserStatusEnum.ACTIVE);
      assertThat(context.getBean(LegalConsentRepository.class).count()).isEqualTo(4);
    });
  }

  /**
   * Converte uma identidade Google já validada em continuação visual e ativação persistida.
   *
   * <p>A validação criptográfica do provedor permanece fora do teste. Da identidade mínima
   * validada em diante, adapter, facade, serviços, prova, aceites e MySQL são reais.
   */
  @Test
  void googleStates_shouldRenderAndCompleteFromPersistedContinuation() {
    contextRunner().run(context -> {
      assertThat(context).hasNotFailed();
      List<String> acceptedIds = seedLegalDocuments(context, "google-states").stream()
          .map(LegalDocumentReferenceVO::reference)
          .toList();

      RFWAuthenticationOutcomeVO resolution = context
          .getBean(RFWExternalIdentityResolver.class)
          .resolve(new RFWVerifiedExternalIdentityVO(
              "google",
              "google-roundtrip-subject",
              "google-roundtrip@example.com",
              true,
              Map.of("iss", "https://accounts.google.com")))
          .toCompletableFuture()
          .join();

      assertThat(resolution.status())
          .isEqualTo(RFWAccessStatusEnum.EXTERNAL_REGISTRATION_REQUIRED);
      assertThat(context.getBean(UserRepository.class).findAll())
          .singleElement()
          .satisfies(user -> assertThat(user.getStatus())
              .isEqualTo(UserStatusEnum.PENDING_VERIFICATION));

      withAttachedExternalRegistrationComponent(context, component -> {
        component.openExternalRegistration(resolution.externalRegistration());
        assertThat(component.getCurrentStep())
            .isEqualTo(RFWAccessStepEnum.EXTERNAL_REGISTRATION);

        component.submitExternalRegistration(new RFWExternalRegistrationRequestDTO(
            resolution.externalRegistration().registrationReference(),
            List.of()));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);
        assertThat(context.getBean(UserRepository.class).findAll().getFirst().getStatus())
            .isEqualTo(UserStatusEnum.PENDING_VERIFICATION);

        Instant changedAt = Instant.now().minusMillis(1);
        new JdbcTemplate(dataSource).update(
            "UPDATE identity_legalDocumentVersion SET retiredAt = ? WHERE retiredAt IS NULL",
            java.sql.Timestamp.from(changedAt));
        LegalDocumentIntegrityService integrity =
            context.getBean(LegalDocumentIntegrityService.class);
        transaction(context).executeWithoutResult(status -> {
          LegalDocumentVersionRepository repository =
              context.getBean(LegalDocumentVersionRepository.class);
          repository.save(new LegalDocumentVersionEntity(
              LegalDocumentTypeEnum.TERMS_OF_USE,
              "terms-google-current",
              true,
              "Termos Google atuais",
              integrity.hash("Termos Google atuais"),
              changedAt,
              null));
          repository.save(new LegalDocumentVersionEntity(
              LegalDocumentTypeEnum.PRIVACY_POLICY,
              "privacy-google-current",
              true,
              "Privacidade Google atual",
              integrity.hash("Privacidade Google atual"),
              changedAt,
              null));
        });

        component.submitExternalRegistration(new RFWExternalRegistrationRequestDTO(
            resolution.externalRegistration().registrationReference(),
            acceptedIds));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);
        List<String> currentIds = component.getLegalDocuments().stream()
            .map(RFWLegalDocumentVO::id)
            .toList();
        assertThat(currentIds).hasSize(2).doesNotContainAnyElementsOf(acceptedIds);

        component.submitExternalRegistration(new RFWExternalRegistrationRequestDTO(
            resolution.externalRegistration().registrationReference(),
            currentIds));
        assertThat(component.getCurrentOutcome()).isNull();
      });

      assertThat(context.getBean(UserRepository.class).findAll().getFirst().getStatus())
          .isEqualTo(UserStatusEnum.ACTIVE);
      assertThat(context.getBean(RegistrationRepository.class).findAll().getFirst().getStatus())
          .isEqualTo(RegistrationStatusEnum.ACTIVE);
      assertThat(context.getBean(ExternalIdentityRepository.class).findAll())
          .singleElement()
          .satisfies(identity -> assertThat(identity.getStatus())
              .isEqualTo(ExternalIdentityStatusEnum.ACTIVE));
      assertThat(context.getBean(LocalCredentialRepository.class).count()).isZero();
      assertThat(context.getBean(LegalConsentRepository.class).count()).isEqualTo(2);
      assertThat(context.getBean(VerificationRepository.class).findAll())
          .singleElement()
          .satisfies(verification -> {
            assertThat(verification.getPurpose())
                .isEqualTo(VerificationPurposeEnum.EXTERNAL_REGISTRATION);
            assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.USED);
          });
    });
  }

  /**
   * Produz solicitação neutra, rejeição e remoção final usando a prova real enviada pós-commit.
   */
  @Test
  void cancellationStates_shouldUsePersistedProofAndRemovePendingRoot() {
    contextRunner().run(context -> {
      assertThat(context).hasNotFailed();
      List<String> acceptedIds = seedLegalDocuments(context, "cancellation").stream()
          .map(LegalDocumentReferenceVO::reference)
          .toList();
      CapturingEmailDispatchService email =
          context.getBean(CapturingEmailDispatchService.class);

      withAttachedComponent(context, component -> {
        component.open(new RFWAccessEntryRequestVO(
            RFWAccessStepEnum.REGISTRATION,
            null,
            null));
        component.submitRegistration(new RFWRegistrationRequestDTO(
            "cancel-states@example.com",
            "R0undtrip!Safe",
            acceptedIds,
            null));

        component.open(new RFWAccessEntryRequestVO(
            RFWAccessStepEnum.REGISTRATION_CANCELLATION_REQUEST,
            "cancel-states@example.com",
            null));
        component.requestRegistrationCancellation(
            new RFWRegistrationCancellationRequestDTO(
                "cancel-states@example.com",
                null));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REGISTRATION_CANCELLATION_REQUIRED);
        assertThat(component.getCurrentStep())
            .isEqualTo(RFWAccessStepEnum.REGISTRATION_CANCELLATION_CONFIRMATION);
        String cancellationProof = UriComponentsBuilder
            .fromUriString(email.lastConfirmationUrl())
            .build()
            .getQueryParams()
            .getFirst("token");
        assertThat(cancellationProof).isNotBlank();

        component.confirmRegistrationCancellation(
            new RFWRegistrationCancellationConfirmationDTO(
                "cancel-states@example.com",
                "invalid-proof"));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.REJECTED);
        assertThat(context.getBean(UserRepository.class).count()).isOne();

        component.confirmRegistrationCancellation(
            new RFWRegistrationCancellationConfirmationDTO(
                "cancel-states@example.com",
                cancellationProof));
        assertThat(component.getCurrentOutcome().status())
            .isEqualTo(RFWAccessStatusEnum.COMPLETED);
        assertThat(component.getCurrentStep()).isEqualTo(RFWAccessStepEnum.RESULT);
      });

      assertThat(context.getBean(UserRepository.class).count()).isZero();
      assertThat(context.getBean(RegistrationRepository.class).count()).isZero();
      assertThat(context.getBean(IdentityEventRepository.class).findAll())
          .singleElement()
          .extracting(event -> event.getEventType())
          .isEqualTo(IdentityEventTypeEnum.REGISTRATION_CANCELLED);
    });
  }

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  private static List<LegalDocumentReferenceVO> seedLegalDocuments(
      ApplicationContext context,
      String suffix) {
    LegalDocumentVersionRepository documentRepository =
        context.getBean(LegalDocumentVersionRepository.class);
    LegalDocumentIntegrityService integrityService =
        context.getBean(LegalDocumentIntegrityService.class);
    transaction(context).executeWithoutResult(status -> {
      documentRepository.save(legalDocument(
          LegalDocumentTypeEnum.TERMS_OF_USE,
          "terms-" + suffix,
          "Termos " + suffix,
          integrityService));
      documentRepository.save(legalDocument(
          LegalDocumentTypeEnum.PRIVACY_POLICY,
          "privacy-" + suffix,
          "Privacidade " + suffix,
          integrityService));
    });
    return context.getBean(LegalDocumentFacade.class).findCurrentDocuments();
  }

  private static void withAttachedComponent(
      ApplicationContext context,
      Consumer<RFWAccessComponent> assertion) {
    RinosAccessComponentFactory hostFactory = new RinosAccessComponentFactory(
        context.getBean(RFWAccessComponentFactory.class),
        context.getBean(LegalDocumentFacade.class));
    withAttachedComponent(
        () -> hostFactory.create("indisponível"),
        assertion);
  }

  private static void withAttachedExternalRegistrationComponent(
      ApplicationContext context,
      Consumer<RFWAccessComponent> assertion) {
    RFWAccessComponentConfig config = RFWAccessComponentConfig.builder()
        .legalDocumentsProvider(() -> context.getBean(LegalDocumentFacade.class)
            .findCurrentDocuments().stream()
            .map(document -> new RFWLegalDocumentVO(
                document.reference(),
                "legal." + document.documentType().name(),
                "/legal-document/" + document.reference(),
                document.required()))
            .toList())
        .build();
    withAttachedComponent(
        () -> context.getBean(RFWAccessComponentFactory.class).create(config),
        assertion);
  }

  private static void withAttachedComponent(
      Supplier<RFWAccessComponent> componentSupplier,
      Consumer<RFWAccessComponent> assertion) {
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
      RFWAccessComponent component = componentSupplier.get();
      ui.add(component);
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
          new MockHttpServletRequest(),
          new MockHttpServletResponse()));
      assertion.accept(component);
    } finally {
      session.unlock();
    }
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
            "rfw.authentication.google.enabled=true",
            "rfw.authentication.google.client-id=roundtrip-client",
            "rfw.authentication.google.issuer=https://accounts.google.com",
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
    RFWAuthenticationSessionService authenticationSessionService() {
      return mock(RFWAuthenticationSessionService.class);
    }

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
    ExternalIdentityService externalIdentityService(ExternalIdentityRepository identities) {
      return new ExternalIdentityService(identities);
    }

    @Bean
    GoogleIdentityResolutionService googleIdentityResolutionService(
        IdentityService identities,
        EmailNormalizationService normalization,
        ExternalIdentityService externalIdentities,
        RegistrationRepository registrations,
        VerificationService verifications,
        IdentityAuditService audit,
        RegistrationPropertiesConfig properties) {
      return new GoogleIdentityResolutionService(
          identities,
          normalization,
          externalIdentities,
          registrations,
          verifications,
          audit,
          properties);
    }

    @Bean
    ExternalRegistrationCompletionService externalRegistrationCompletionService(
        VerificationService verifications,
        LegalConsentService consents,
        LocalCredentialService credentials,
        ExternalIdentityService externalIdentities,
        IdentityAuditService audit) {
      return new ExternalRegistrationCompletionService(
          verifications,
          consents,
          credentials,
          externalIdentities,
          new UserLifecycleService(),
          new RegistrationLifecycleService(),
          audit);
    }

    @Bean
    RegistrationActivationService registrationActivationService(
        VerificationService verifications,
        LegalConsentService consents,
        ExternalIdentityService externalIdentities,
        IdentityAuditService audit) {
      return new RegistrationActivationService(
          verifications,
          consents,
          new UserLifecycleService(),
          new RegistrationLifecycleService(),
          externalIdentities,
          audit,
          new EmailPrivacyService());
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
    RegistrationResendService registrationResendService(
        RegistrationRepository registrations,
        IdentityEventRepository events,
        VerificationService verifications,
        IdentityAuditService audit,
        PublicApplicationUriService uriService,
        VerificationEmailDispatchService dispatch,
        RegistrationPropertiesConfig properties) {
      return new RegistrationResendService(
          registrations,
          events,
          verifications,
          audit,
          uriService,
          dispatch,
          properties);
    }

    @Bean
    RegistrationCancellationService registrationCancellationService(
        RegistrationRepository registrations,
        IdentityEventRepository events,
        UserRepository users,
        VerificationService verifications,
        EmailNormalizationService normalization,
        IdentityAuditService audit,
        PublicApplicationUriService uriService,
        VerificationEmailDispatchService dispatch,
        RegistrationPropertiesConfig properties) {
      return new RegistrationCancellationService(
          registrations,
          events,
          users,
          verifications,
          normalization,
          new RegistrationLifecycleService(),
          new UserLifecycleService(),
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
    RegistrationResendFacade registrationResendFacade(
        IdentityService identities,
        RegistrationResendService resend,
        RegistrationObservabilityService observability) {
      return new RegistrationResendFacadeImpl(identities, resend, observability);
    }

    @Bean
    RegistrationActivationFacade registrationActivationFacade(
        RegistrationActivationService activation,
        RegistrationObservabilityService observability) {
      return new RegistrationActivationFacadeImpl(activation, observability);
    }

    @Bean
    RegistrationCancellationFacade registrationCancellationFacade(
        IdentityService identities,
        RegistrationCancellationService cancellation,
        RegistrationObservabilityService observability) {
      return new RegistrationCancellationFacadeImpl(
          identities,
          cancellation,
          new VerificationPropertiesConfig(Duration.ofHours(24)),
          observability);
    }

    @Bean
    GoogleIdentityResolutionFacade googleIdentityResolutionFacade(
        GoogleIdentityResolutionService resolution,
        RFWAuthenticationPropertiesConfig authenticationProperties) {
      return new GoogleIdentityResolutionFacadeImpl(resolution, authenticationProperties);
    }

    @Bean
    GoogleAuthenticationFacade googleAuthenticationFacade() {
      return request -> java.util.concurrent.CompletableFuture.completedFuture(
          br.com.rinos.app.api.vo.GoogleAuthenticationResultVO.identityNotFound());
    }

    @Bean
    RFWAuthenticationOutcomeAdapter authenticationOutcomeAdapter() {
      return new RFWAuthenticationOutcomeAdapter();
    }

    @Bean
    ExternalRegistrationFacade externalRegistrationFacade(
        ExternalRegistrationCompletionService completion) {
      return new ExternalRegistrationFacadeImpl(completion);
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

    @Bean
    RFWActivationConsentProviderAdapter activationConsentProvider(
        RegistrationActivationFacade activation) {
      return new RFWActivationConsentProviderAdapter(activation);
    }

    @Bean
    RFWRegistrationCancellationProviderAdapter registrationCancellationProvider(
        RegistrationCancellationFacade cancellation) {
      return new RFWRegistrationCancellationProviderAdapter(cancellation);
    }

    @Bean
    RFWExternalIdentityResolverAdapter externalIdentityResolver(
        GoogleAuthenticationFacade authentication,
        GoogleIdentityResolutionFacade resolution,
        RFWAuthenticationOutcomeAdapter outcomeAdapter) {
      return new RFWExternalIdentityResolverAdapter(
          authentication, resolution, outcomeAdapter);
    }

    @Bean
    RFWExternalRegistrationProviderAdapter externalRegistrationProvider(
        ExternalRegistrationFacade completion) {
      return new RFWExternalRegistrationProviderAdapter(completion);
    }
  }

  /** Isola os limites ampliados do gate sem alterar os defaults da aplicação ou dos demais testes. */
  @Configuration(proxyBeanMethods = false)
  static class SmtpGateOriginConfig {

    /**
     * Permite a amostra de cem pendências e mantém uma margem explícita antes do limite absoluto.
     *
     * @param origins repositório real do contador global
     * @return serviço exclusivo do gate SMTP
     */
    @Bean
    @Primary
    OriginLimitService smtpGateOriginLimitService(OriginWindowRepository origins) {
      return new OriginLimitService(
          origins,
          new OriginPropertiesConfig(100, 120, Duration.ofHours(24), Duration.ofDays(30)));
    }
  }

  /**
   * Monta a pipeline real de templates e SMTP do RFW contra a porta dinâmica do gate.
   *
   * @param port porta do servidor SMTP local
   * @return fachada RFW pronta para renderizar e enviar mensagens reais
   */
  private static EmailDispatchService localSmtpEmailDispatchService(int port) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost("127.0.0.1");
    mailSender.setPort(port);
    mailSender.setProtocol("smtp");
    mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    Properties mailProperties = mailSender.getJavaMailProperties();
    mailProperties.setProperty("mail.smtp.auth", "false");
    mailProperties.setProperty("mail.smtp.starttls.enable", "false");
    mailProperties.setProperty("mail.smtp.connectiontimeout", "5000");
    mailProperties.setProperty("mail.smtp.timeout", "5000");
    mailProperties.setProperty("mail.smtp.writetimeout", "5000");

    EmailTemplatePropertiesConfig templateProperties = new EmailTemplatePropertiesConfig();
    templateProperties.setDefaultFromAddress("no-reply@rinos.test");
    return new EmailDispatchService(
        new ClasspathEmailTemplateResolver(new DefaultResourceLoader(), templateProperties),
        new PositionalEmailTemplateRenderer(),
        new SmtpEmailDispatcher(mailSender, templateProperties, mock(RFWLogger.class)));
  }

  /**
   * Persiste em {@code target} as amostras técnicas sanitizadas da execução.
   *
   * @param samples medidas individuais sem destinatários ou provas
   * @param metrics registro que confirma as mesmas aceitações pelo serviço de produção
   */
  private static void writeSmtpGateReport(
      List<SmtpGateSample> samples,
      SimpleMeterRegistry metrics) {
    try {
      List<Long> orderedMilliseconds = samples.stream()
          .map(sample -> sample.commitToAcceptanceUpperBound().toMillis())
          .sorted(Comparator.naturalOrder())
          .toList();
      long median = percentile(orderedMilliseconds, 0.50d);
      long percentile95 = percentile(orderedMilliseconds, 0.95d);
      long maximum = orderedMilliseconds.getLast();
      long smtpMetricCount = metrics.find(SMTP_DURATION_METRIC)
          .tag("result", "accepted")
          .timer()
          .count();
      StringBuilder json = new StringBuilder(16_384);
      json.append("{\n")
          .append("  \"sampleCount\": ").append(samples.size()).append(",\n")
          .append("  \"acceptedCount\": ").append(samples.size()).append(",\n")
          .append("  \"smtpMetricCount\": ").append(smtpMetricCount).append(",\n")
          .append("  \"medianCommitToAcceptanceUpperBoundMillis\": ")
          .append(median).append(",\n")
          .append("  \"p95CommitToAcceptanceUpperBoundMillis\": ")
          .append(percentile95).append(",\n")
          .append("  \"maxCommitToAcceptanceUpperBoundMillis\": ")
          .append(maximum).append(",\n")
          .append("  \"samples\": [\n");
      for (int index = 0; index < samples.size(); index++) {
        SmtpGateSample sample = samples.get(index);
        json.append("    {\"sequence\": ")
            .append(sample.sequence())
            .append(", \"correlationId\": \"")
            .append(sample.correlationId())
            .append("\", \"commitToAcceptanceUpperBoundMillis\": ")
            .append(sample.commitToAcceptanceUpperBound().toMillis())
            .append('}');
        json.append(index + 1 == samples.size() ? '\n' : ",\n");
      }
      json.append("  ]\n}\n");
      Path reportDirectory = Files.createDirectories(Path.of("target", "smtp-gate"));
      Files.writeString(
          reportDirectory.resolve("7.4.1.json"),
          json,
          StandardCharsets.UTF_8);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Não foi possível gravar o relatório do gate SMTP.", failure);
    }
  }

  /**
   * Seleciona um percentil nearest-rank de uma lista previamente ordenada.
   *
   * @param orderedValues valores crescentes
   * @param percentile percentil entre zero e um
   * @return valor que ocupa o rank solicitado
   */
  private static long percentile(List<Long> orderedValues, double percentile) {
    int index = (int) Math.ceil(percentile * orderedValues.size()) - 1;
    return orderedValues.get(Math.max(0, index));
  }

  /** Amostra sanitizada do limite superior entre o commit e a aceitação SMTP. */
  private record SmtpGateSample(
      int sequence,
      UUID correlationId,
      Duration commitToAcceptanceUpperBound) {
  }

  /** Simula somente o transporte SMTP e preserva a mensagem final para a asserção. */
  static final class CapturingEmailDispatchService extends EmailDispatchService {

    private final AtomicReference<EmailMessage> lastMessage = new AtomicReference<>();
    private final AtomicReference<String> lastConfirmationUrl = new AtomicReference<>();
    private final AtomicReference<String> lastManualCode = new AtomicReference<>();

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
      lastConfirmationUrl.set(params.length > 0 ? String.valueOf(params[0]) : null);
      lastManualCode.set(params.length > 2 ? String.valueOf(params[2]) : null);
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

    String lastConfirmationUrl() {
      return lastConfirmationUrl.get();
    }

    String lastManualCode() {
      return lastManualCode.get();
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
