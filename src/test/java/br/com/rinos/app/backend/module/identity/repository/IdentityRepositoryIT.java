package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.entity.LegalConsentEntity;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.OriginWindowEntity;
import br.com.rinos.app.backend.module.identity.entity.PasswordRecoveryEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.entity.VerificationEntity;
import br.com.rinos.app.backend.module.identity.service.RegistrationAuthenticationContinuationService;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.GoogleIdentityDomainStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginPolicyEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.service.LocalCredentialService;
import br.com.rinos.app.backend.module.identity.service.ExternalIdentityService;
import br.com.rinos.app.backend.module.identity.service.ExternalRegistrationCompletionService;
import br.com.rinos.app.backend.module.identity.service.EmailPrivacyService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.LegalDocumentIntegrityService;
import br.com.rinos.app.backend.module.identity.service.EmailNormalizationService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.GoogleIdentityResolutionService;
import br.com.rinos.app.backend.module.identity.service.PublicApplicationUriService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCancellationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationActivationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationLifecycleService;
import br.com.rinos.app.backend.module.identity.service.RegistrationExpiryCleanupService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationResendService;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.service.UserLifecycleService;
import br.com.rinos.app.backend.module.identity.service.VerificationService;
import br.com.rinos.app.backend.module.identity.service.VerificationEmailDispatchService;
import br.com.rinos.app.backend.module.identity.service.VerificationTokenService;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.GoogleIdentityDomainResultVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationResendTransactionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.config.VerificationPropertiesConfig;
import br.com.rinos.app.config.CleanupPropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.com.rinos.app.config.OriginPropertiesConfig;
import br.com.rinos.app.config.RegistrationPropertiesConfig;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.platform.service.MaintenanceCoordinatorService;

/**
 * Valida unicidade, relacionamento e controle otimista da identidade contra MySQL 9.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@DisplayName("Persistência da identidade global")
class IdentityRepositoryIT {

  private static MySqlTestDatabase testDatabase;

  private DataSource dataSource;

  /**
   * Seleciona o provedor MySQL e reserva o schema exclusivo da classe.
   */
  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  /**
   * Remove o schema exclusivo e encerra eventual contêiner.
   */
  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  /**
   * Recria o schema global real antes de cada cenário.
   */
  @BeforeEach
  void resetDatabase() {
    Assumptions.assumeTrue(
        testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql")).execute(dataSource);
  }

  /**
   * Comprova que a constraint física impede duas identidades para a mesma chave.
   */
  @Test
  void save_shouldRejectDuplicate_whenNormalizedEmailAlreadyExists() {
    contextRunner().run(context -> {
      UserRepository repository = context.getBean(UserRepository.class);
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> repository.saveAndFlush(user("First@Example.com")));

      assertThatThrownBy(() -> transaction.executeWithoutResult(
          status -> repository.saveAndFlush(user("SECOND@example.com"))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(repository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova que a constraint de e-mail escolhe um único vencedor sob disputa real.
   *
   * @throws Exception quando a coordenação local das escritas falha
   */
  @Test
  void save_shouldChooseOneUser_whenNormalizedEmailIsWrittenConcurrently() throws Exception {
    contextRunner().run(context -> {
      UserRepository repository = context.getBean(UserRepository.class);
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<Boolean> first = executor.submit(() -> saveAfterSignal(
            start,
            transaction(context),
            () -> repository.saveAndFlush(user("first@example.com"))));
        Future<Boolean> second = executor.submit(() -> saveAfterSignal(
            start,
            transaction(context),
            () -> repository.saveAndFlush(user("second@example.com"))));
        start.countDown();

        assertThat(List.of(
            first.get(10, TimeUnit.SECONDS),
            second.get(10, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(true, false);
        assertThat(repository.count()).isEqualTo(1);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  /**
   * Comprova que somente um processo 1:1 pode apontar para a identidade.
   */
  @Test
  void save_shouldRejectSecondRegistration_whenUserAlreadyHasRegistration() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      TransactionTemplate transaction = transaction(context);
      UserEntity persistedUser = transaction.execute(
          status -> userRepository.saveAndFlush(user("user@example.com")));
      transaction.executeWithoutResult(status -> registrationRepository.saveAndFlush(
          registration(persistedUser, RegistrationMethodEnum.LOCAL)));

      assertThatThrownBy(() -> transaction.executeWithoutResult(
          status -> registrationRepository.saveAndFlush(
              registration(persistedUser, RegistrationMethodEnum.GOOGLE))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(registrationRepository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova unicidade da prova e bloqueio das recuperações abertas por usuário.
   */
  @Test
  void passwordRecovery_shouldPersistOnlyHashAndInvalidatePreviousProof() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      PasswordRecoveryRepository recoveryRepository =
          context.getBean(PasswordRecoveryRepository.class);
      TransactionTemplate transaction = transaction(context);
      UserEntity user = transaction.execute(status -> userRepository.saveAndFlush(
          new UserEntity(
              "recovery@example.test",
              "recovery@example.test",
              UserStatusEnum.ACTIVE)));
      Instant now = Instant.parse("2026-08-02T12:00:00Z");
      byte[] hash = new byte[32];
      hash[0] = 42;
      transaction.executeWithoutResult(status -> recoveryRepository.saveAndFlush(
          new PasswordRecoveryEntity(user, hash, now, now.plus(Duration.ofHours(1)))));

      transaction.executeWithoutResult(status -> {
        List<PasswordRecoveryEntity> open = recoveryRepository
            .findByUserIdAndStatusForUpdate(user.getId(), PasswordRecoveryStatusEnum.OPEN);
        assertThat(open).hasSize(1);
        open.getFirst().setStatus(PasswordRecoveryStatusEnum.INVALIDATED);
        open.getFirst().setInvalidatedAt(now.plusSeconds(1));
      });

      assertThat(recoveryRepository.count()).isEqualTo(1);
      assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
          recoveryRepository.saveAndFlush(new PasswordRecoveryEntity(
              user, hash, now.plusSeconds(2), now.plus(Duration.ofHours(2))))))
          .isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  /**
   * Comprova que uma cópia obsoleta não sobrescreve uma transição já confirmada.
   */
  @Test
  void save_shouldRejectStaleEntity_whenVersionWasAdvancedByAnotherTransaction() {
    contextRunner().run(context -> {
      UserRepository repository = context.getBean(UserRepository.class);
      TransactionTemplate transaction = transaction(context);
      UserEntity persisted = transaction.execute(
          status -> repository.saveAndFlush(user("user@example.com")));
      UserEntity firstCopy = transaction.execute(
          status -> repository.findById(persisted.getId()).orElseThrow());
      UserEntity staleCopy = transaction.execute(
          status -> repository.findById(persisted.getId()).orElseThrow());

      firstCopy.setStatus(UserStatusEnum.ACTIVE);
      transaction.executeWithoutResult(status -> repository.saveAndFlush(firstCopy));
      staleCopy.setStatus(UserStatusEnum.CANCELLED);

      assertThatThrownBy(() -> transaction.executeWithoutResult(
          status -> repository.saveAndFlush(staleCopy)))
          .isInstanceOf(OptimisticLockingFailureException.class);
      UserEntity current = repository.findById(persisted.getId()).orElseThrow();
      assertThat(current.getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
      assertThat(current.getVersion()).isEqualTo(1);
    });
  }

  /**
   * Comprova que o bloqueio pessimista permite exatamente um consumo concorrente.
   *
   * @throws Exception quando a coordenação local dos consumidores falha
   */
  @Test
  void consume_shouldChooseOneWinner_whenTwoTransactionsPresentSameToken() throws Exception {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      TransactionTemplate transaction = transaction(context);
      VerificationTokenService tokenService = new VerificationTokenService();
      String token = tokenService.generate();
      RegistrationEntity registration = transaction.execute(status -> {
        UserEntity persistedUser = userRepository.saveAndFlush(user("user@example.com"));
        RegistrationEntity persistedRegistration = registrationRepository.saveAndFlush(
            registration(persistedUser, RegistrationMethodEnum.LOCAL));
        verificationRepository.saveAndFlush(new VerificationEntity(
            persistedRegistration,
            VerificationPurposeEnum.REGISTRATION_EMAIL,
            tokenService.hash(token),
            Instant.parse("2026-07-29T18:00:00Z"),
            Instant.parse("2026-07-30T18:00:00Z")));
        return persistedRegistration;
      });
      VerificationService service = new VerificationService(
          verificationRepository,
          registrationRepository,
          tokenService,
          new VerificationPropertiesConfig(Duration.ofHours(24)));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<VerificationConsumptionStatusEnum> first = executor.submit(
            () -> consumeAfterSignal(
                start,
                transaction(context),
                service,
                registration.getId(),
                token));
        Future<VerificationConsumptionStatusEnum> second = executor.submit(
            () -> consumeAfterSignal(
                start,
                transaction(context),
                service,
                registration.getId(),
                token));
        start.countDown();

        List<VerificationConsumptionStatusEnum> results =
            List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        assertThat(results)
            .containsExactlyInAnyOrder(
                VerificationConsumptionStatusEnum.VERIFIED,
                VerificationConsumptionStatusEnum.REJECTED);
        VerificationEntity persisted = verificationRepository.findAll().getFirst();
        assertThat(persisted.getStatus()).isEqualTo(VerificationStatusEnum.USED);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  /**
   * Comprova reenvio real no MySQL, invalidando a prova anterior e persistindo somente uma prova aberta.
   */
  @Test
  void resend_shouldInvalidatePreviousProofAndPersistNewOpenProof() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      VerificationService verificationService = new VerificationService(
          verificationRepository,
          registrationRepository,
          new VerificationTokenService(),
          new VerificationPropertiesConfig(Duration.ofHours(24)));
      RegistrationEntity persistedRegistration = transaction.execute(status -> {
        UserEntity persistedUser = userRepository.saveAndFlush(new UserEntity(
            "resend-mysql@example.com",
            "resend-mysql@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        RegistrationEntity registration = registrationRepository.saveAndFlush(
            registration(persistedUser, RegistrationMethodEnum.LOCAL));
        verificationService.issue(
            registration,
            VerificationPurposeEnum.REGISTRATION_EMAIL,
            Instant.parse("2026-07-29T18:00:00Z"));
        return registration;
      });
      PublicApplicationUriService uriService = mock(PublicApplicationUriService.class);
      when(uriService.activationUri(any())).thenReturn(
          URI.create("http://localhost:7070/login?step=activation&proof=opaque"));
      VerificationEmailDispatchService dispatchService =
          mock(VerificationEmailDispatchService.class);
      when(dispatchService.scheduleAfterCommit(any())).thenReturn(
          CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
              VerificationEmailDispatchStatusEnum.ACCEPTED,
              java.util.UUID.randomUUID(),
              Duration.ZERO)));
      RegistrationResendService resendService = new RegistrationResendService(
          registrationRepository,
          eventRepository,
          verificationService,
          new IdentityAuditService(eventRepository),
          uriService,
          dispatchService,
          new RegistrationPropertiesConfig(
              Duration.ofDays(15),
              3,
              Duration.ofMinutes(15),
              3,
              Duration.ofMinutes(15)));

      RegistrationResendTransactionVO result = transaction.execute(status -> resendService.resend(
          persistedRegistration.getId(),
          java.util.Locale.of("pt", "BR"),
          java.util.UUID.randomUUID(),
          Instant.parse("2026-07-29T18:05:00Z")));

      assertThat(result).isNotNull();
      assertThat(result.eligible()).isTrue();
      assertThat(result.blocked()).isFalse();
      assertThat(result.dispatch().toCompletableFuture().join().accepted()).isTrue();
      assertThat(verificationRepository.findAll())
          .extracting(VerificationEntity::getStatus)
          .containsExactlyInAnyOrder(
              VerificationStatusEnum.INVALIDATED,
              VerificationStatusEnum.OPEN);
      assertThat(eventRepository.findAll())
          .extracting(IdentityEventEntity::getEventType)
          .contains(IdentityEventTypeEnum.VERIFICATION_REISSUED);
    });
  }

  /**
   * Comprova ativação local integral no MySQL com prova usada e credencial preservada.
   */
  @Test
  void activateLocalRegistration_shouldCommitLifecycleAndUsedProof() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LocalCredentialRepository credentialRepository =
          context.getBean(LocalCredentialRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      LegalDocumentVersionRepository documentRepository =
          context.getBean(LegalDocumentVersionRepository.class);
      LegalConsentRepository consentRepository = context.getBean(LegalConsentRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      Instant occurredAt = Instant.parse("2026-07-29T18:00:00Z");
      VerificationService verificationService = new VerificationService(
          verificationRepository,
          registrationRepository,
          new VerificationTokenService(),
          new VerificationPropertiesConfig(Duration.ofHours(24)));
      LegalConsentService legalConsentService =
          new LegalConsentService(documentRepository, consentRepository);
      RegistrationActivationService activationService = new RegistrationActivationService(
          verificationService,
          legalConsentService,
          lifecycleWithPersonalContract(),
          new RegistrationLifecycleService(),
          new ExternalIdentityService(externalRepository),
          new IdentityAuditService(eventRepository),
          new EmailPrivacyService(),
          registrationContinuationService());
      String proof = transaction.execute(status -> {
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
            "local-activation@example.com",
            "local-activation@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        RegistrationEntity registration = registrationRepository.saveAndFlush(
            registration(user, RegistrationMethodEnum.LOCAL));
        credentialRepository.saveAndFlush(new LocalCredentialEntity(
            user,
            "{argon2}encoded-value"));
        LegalDocumentVersionEntity terms = documentRepository.saveAndFlush(legalDocument(
            LegalDocumentTypeEnum.TERMS_OF_USE,
            "terms-local-activation",
            occurredAt.minusSeconds(60)));
        LegalDocumentVersionEntity privacy = documentRepository.saveAndFlush(legalDocument(
            LegalDocumentTypeEnum.PRIVACY_POLICY,
            "privacy-local-activation",
            occurredAt.minusSeconds(60)));
        legalConsentService.recordCurrentDecisions(
            user,
            registration,
            Map.of(
                terms.getId(), LegalConsentDecisionEnum.ACCEPTED,
                privacy.getId(), LegalConsentDecisionEnum.ACCEPTED),
            occurredAt);
        return verificationService.issue(
            registration,
            VerificationPurposeEnum.REGISTRATION_EMAIL,
            occurredAt).getToken();
      });

      RegistrationActivationResultVO result = transaction.execute(status -> activationService.activate(
          proof,
          java.util.UUID.randomUUID(),
          occurredAt.plusSeconds(60)));

      assertThat(result.status()).isEqualTo(RegistrationActivationStatusEnum.ACTIVATED);
      assertThat(userRepository.findAll().getFirst().getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
      assertThat(registrationRepository.findAll().getFirst().getStatus())
          .isEqualTo(RegistrationStatusEnum.ACTIVE);
      assertThat(credentialRepository.findAll().getFirst().getStatus())
          .isEqualTo(LocalCredentialStatusEnum.ACTIVE);
      assertThat(verificationRepository.findAll().getFirst().getStatus())
          .isEqualTo(VerificationStatusEnum.USED);
      assertThat(consentRepository.count()).isEqualTo(2);
    });
  }

  /**
   * Comprova que uma identidade Google nova cria uma única raiz pendente e uma continuação opaca.
   */
  @Test
  void resolveGoogle_shouldPersistNewPendingIdentityAndContinuation() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      TransactionTemplate transaction = transaction(context);
      Instant occurredAt = Instant.parse("2026-07-29T18:00:00Z");

      GoogleIdentityDomainResultVO result = transaction.execute(status ->
          googleResolutionService(context).resolve(
              "https://accounts.google.com",
              "subject-new",
              "new-google@example.com",
              java.util.UUID.randomUUID(),
              occurredAt));

      assertThat(result).isNotNull();
      assertThat(result.status())
          .isEqualTo(GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED);
      assertThat(result.continuationToken()).isNotBlank();
      assertThat(userRepository.findAll())
          .singleElement()
          .extracting(UserEntity::getStatus)
          .isEqualTo(UserStatusEnum.PENDING_VERIFICATION);
      assertThat(registrationRepository.findAll())
          .singleElement()
          .extracting(RegistrationEntity::getMethod)
          .isEqualTo(RegistrationMethodEnum.GOOGLE);
      assertThat(externalRepository.findAll())
          .singleElement()
          .extracting(ExternalIdentityEntity::getStatus)
          .isEqualTo(ExternalIdentityStatusEnum.PENDING);
      assertThat(verificationRepository.findAll())
          .singleElement()
          .satisfies(verification -> {
            assertThat(verification.getPurpose())
                .isEqualTo(VerificationPurposeEnum.EXTERNAL_REGISTRATION);
            assertThat(verification.getStatus()).isEqualTo(VerificationStatusEnum.OPEN);
          });
    });
  }

  /**
   * Comprova a reutilização Google de uma pendência local sem apagar antecipadamente sua senha.
   */
  @Test
  void resolveGoogle_shouldReuseLocalPendingIdentityAndPreserveCredential() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LocalCredentialRepository credentialRepository =
          context.getBean(LocalCredentialRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      TransactionTemplate transaction = transaction(context);
      Instant occurredAt = Instant.parse("2026-07-29T18:00:00Z");
      transaction.executeWithoutResult(status -> {
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
            "pending-google@example.com",
            "pending-google@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        registrationRepository.saveAndFlush(new RegistrationEntity(
            user,
            RegistrationMethodEnum.LOCAL,
            RegistrationStatusEnum.PENDING_VERIFICATION,
            occurredAt.plus(Duration.ofDays(15))));
        credentialRepository.saveAndFlush(new LocalCredentialEntity(
            user,
            "{argon2}encoded-value"));
      });

      GoogleIdentityDomainResultVO result = transaction.execute(status ->
          googleResolutionService(context).resolve(
              "https://accounts.google.com",
              "subject-pending",
              "pending-google@example.com",
              java.util.UUID.randomUUID(),
              occurredAt));

      assertThat(result).isNotNull();
      assertThat(result.status())
          .isEqualTo(GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED);
      assertThat(userRepository.count()).isOne();
      assertThat(registrationRepository.findAll().getFirst().getMethod())
          .isEqualTo(RegistrationMethodEnum.LOCAL);
      assertThat(credentialRepository.findAll())
          .singleElement()
          .extracting(LocalCredentialEntity::getStatus)
          .isEqualTo(LocalCredentialStatusEnum.ACTIVE);
      assertThat(externalRepository.findAll())
          .singleElement()
          .extracting(ExternalIdentityEntity::getStatus)
          .isEqualTo(ExternalIdentityStatusEnum.PENDING);
      assertThat(verificationRepository.findAll())
          .singleElement()
          .extracting(VerificationEntity::getPurpose)
          .isEqualTo(VerificationPurposeEnum.EXTERNAL_REGISTRATION);
    });
  }

  /**
   * Comprova que e-mail já ativo exige reautenticação e não cria vínculo Google implicitamente.
   */
  @Test
  void resolveGoogle_shouldRequireReauthenticationWithoutLinkingActiveUser() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      Instant occurredAt = Instant.parse("2026-07-29T18:00:00Z");
      transaction.executeWithoutResult(status -> userRepository.saveAndFlush(new UserEntity(
          "active-google@example.com",
          "active-google@example.com",
          UserStatusEnum.ACTIVE)));

      GoogleIdentityDomainResultVO result = transaction.execute(status ->
          googleResolutionService(context).resolve(
              "https://accounts.google.com",
              "subject-active",
              "active-google@example.com",
              java.util.UUID.randomUUID(),
              occurredAt));

      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(
          GoogleIdentityDomainStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED);
      assertThat(externalRepository.count()).isZero();
      assertThat(verificationRepository.count()).isZero();
      assertThat(eventRepository.findAll())
          .singleElement()
          .extracting(IdentityEventEntity::getReason)
          .isEqualTo(
              GoogleIdentityDomainStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED.name());
    });
  }

  /**
   * Comprova que a conversão Google não deixa hash local persistido.
   */
  @Test
  void invalidateAndRemoveForGoogle_shouldDeleteCredential_whenTransactionCommits() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      LocalCredentialRepository credentialRepository =
          context.getBean(LocalCredentialRepository.class);
      TransactionTemplate transaction = transaction(context);
      UserEntity persistedUser = transaction.execute(
          status -> userRepository.saveAndFlush(user("user@example.com")));
      transaction.executeWithoutResult(status -> credentialRepository.saveAndFlush(
          new LocalCredentialEntity(persistedUser, "{argon2}encoded-value")));
      LocalCredentialService service = new LocalCredentialService(credentialRepository);

      Boolean removed = transaction.execute(status -> service.invalidateAndRemoveForGoogle(
          persistedUser.getId(),
          Instant.parse("2026-07-29T18:00:00Z")));

      assertThat(removed).isTrue();
      assertThat(credentialRepository.count()).isZero();
      assertThat(credentialRepository.findByUserIdAndStatus(
          persistedUser.getId(),
          LocalCredentialStatusEnum.ACTIVE)).isEmpty();
    });
  }

  /**
   * Comprova o commit integral da conversão Google e a rejeição de replay.
   */
  @Test
  void completeExternalRegistration_shouldCommitActivationWithoutLocalCredential() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LocalCredentialRepository credentialRepository =
          context.getBean(LocalCredentialRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      LegalDocumentVersionRepository documentRepository =
          context.getBean(LegalDocumentVersionRepository.class);
      LegalConsentRepository consentRepository =
          context.getBean(LegalConsentRepository.class);
      IdentityEventRepository eventRepository =
          context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      Instant occurredAt = Instant.parse("2026-07-29T18:00:00Z");

      VerificationService verificationService = new VerificationService(
          verificationRepository,
          registrationRepository,
          new VerificationTokenService(),
          new VerificationPropertiesConfig(Duration.ofHours(24)));
      LegalConsentService legalConsentService =
          new LegalConsentService(documentRepository, consentRepository);
      ExternalIdentityService externalIdentityService =
          new ExternalIdentityService(externalRepository);
      ExternalRegistrationCompletionService completionService =
          new ExternalRegistrationCompletionService(
              verificationService,
              legalConsentService,
              new LocalCredentialService(credentialRepository),
              externalIdentityService,
              lifecycleWithPersonalContract(),
              new RegistrationLifecycleService(),
              new IdentityAuditService(eventRepository),
              registrationContinuationService());

      ExternalRegistrationFixture fixture = transaction.execute(status -> {
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
            "google-completion@example.com",
            "google-completion@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        RegistrationEntity registration = registrationRepository.saveAndFlush(
            registration(user, RegistrationMethodEnum.LOCAL));
        credentialRepository.saveAndFlush(
            new LocalCredentialEntity(user, "{argon2}encoded-value"));
        externalRepository.saveAndFlush(new ExternalIdentityEntity(
            user,
            ExternalIdentityProviderEnum.GOOGLE,
            "https://accounts.google.com",
            "completion-subject",
            occurredAt));
        LegalDocumentVersionEntity terms = legalDocument(
            LegalDocumentTypeEnum.TERMS_OF_USE,
            "terms-google",
            occurredAt.minusSeconds(60));
        LegalDocumentVersionEntity privacy = legalDocument(
            LegalDocumentTypeEnum.PRIVACY_POLICY,
            "privacy-google",
            occurredAt.minusSeconds(60));
        documentRepository.saveAllAndFlush(List.of(terms, privacy));
        String proof = verificationService.issue(
            registration,
            VerificationPurposeEnum.EXTERNAL_REGISTRATION,
            occurredAt).getToken();
        return new ExternalRegistrationFixture(
            user.getId(),
            registration.getId(),
            terms.getId(),
            privacy.getId(),
            proof);
      });

      ExternalRegistrationCompletionResultVO completed = transaction.execute(
          status -> completionService.complete(
              fixture.proof(),
              List.of(fixture.termsId(), fixture.privacyId()),
              java.util.UUID.randomUUID(),
              occurredAt.plusSeconds(60)));
      ExternalRegistrationCompletionResultVO replay = transaction.execute(
          status -> completionService.complete(
              fixture.proof(),
              List.of(fixture.termsId(), fixture.privacyId()),
              java.util.UUID.randomUUID(),
              occurredAt.plusSeconds(120)));

      assertThat(completed.status())
          .isEqualTo(ExternalRegistrationCompletionStatusEnum.AUTHENTICATED);
      assertThat(replay.status())
          .isEqualTo(ExternalRegistrationCompletionStatusEnum.INVALID_REFERENCE);
      assertThat(userRepository.findById(fixture.userId()).orElseThrow().getStatus())
          .isEqualTo(UserStatusEnum.ACTIVE);
      assertThat(registrationRepository.findById(fixture.registrationId())
          .orElseThrow().getStatus()).isEqualTo(RegistrationStatusEnum.ACTIVE);
      assertThat(externalRepository.findAll().getFirst().getStatus())
          .isEqualTo(ExternalIdentityStatusEnum.ACTIVE);
      assertThat(credentialRepository.count()).isZero();
      assertThat(consentRepository.count()).isEqualTo(2);
    });
  }

  /**
   * Comprova que ativação local e conclusão Google possuem um único vencedor transacional.
   */
  @Test
  void activationRace_shouldCommitExactlyOneLocalOrGoogleWinner() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LocalCredentialRepository credentialRepository =
          context.getBean(LocalCredentialRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      LegalDocumentVersionRepository documentRepository =
          context.getBean(LegalDocumentVersionRepository.class);
      LegalConsentRepository consentRepository =
          context.getBean(LegalConsentRepository.class);
      IdentityEventRepository eventRepository =
          context.getBean(IdentityEventRepository.class);
      Instant occurredAt = Instant.parse("2026-07-29T18:00:00Z");

      VerificationService verificationService = new VerificationService(
          verificationRepository,
          registrationRepository,
          new VerificationTokenService(),
          new VerificationPropertiesConfig(Duration.ofHours(24)));
      LegalConsentService legalConsentService =
          new LegalConsentService(documentRepository, consentRepository);
      ExternalIdentityService externalIdentityService =
          new ExternalIdentityService(externalRepository);
      IdentityAuditService auditService = new IdentityAuditService(eventRepository);
      RegistrationActivationService localService = new RegistrationActivationService(
          verificationService,
          legalConsentService,
          lifecycleWithPersonalContract(),
          new RegistrationLifecycleService(),
          externalIdentityService,
          auditService,
          new EmailPrivacyService(),
          registrationContinuationService());
      ExternalRegistrationCompletionService googleService =
          new ExternalRegistrationCompletionService(
              verificationService,
              legalConsentService,
              new LocalCredentialService(credentialRepository),
              externalIdentityService,
              lifecycleWithPersonalContract(),
              new RegistrationLifecycleService(),
              auditService,
              registrationContinuationService());

      ActivationRaceFixture fixture = transaction(context).execute(status -> {
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
            "activation-race@example.com",
            "activation-race@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        RegistrationEntity registration = registrationRepository.saveAndFlush(
            registration(user, RegistrationMethodEnum.LOCAL));
        credentialRepository.saveAndFlush(
            new LocalCredentialEntity(user, "{argon2}encoded-value"));
        externalRepository.saveAndFlush(new ExternalIdentityEntity(
            user,
            ExternalIdentityProviderEnum.GOOGLE,
            "https://accounts.google.com",
            "race-subject",
            occurredAt));
        LegalDocumentVersionEntity terms = legalDocument(
            LegalDocumentTypeEnum.TERMS_OF_USE,
            "terms-race",
            occurredAt.minusSeconds(60));
        LegalDocumentVersionEntity privacy = legalDocument(
            LegalDocumentTypeEnum.PRIVACY_POLICY,
            "privacy-race",
            occurredAt.minusSeconds(60));
        documentRepository.saveAllAndFlush(List.of(terms, privacy));
        Map<Long, LegalConsentDecisionEnum> decisions = Map.of(
            terms.getId(),
            LegalConsentDecisionEnum.ACCEPTED,
            privacy.getId(),
            LegalConsentDecisionEnum.ACCEPTED);
        legalConsentService.recordCurrentDecisions(
            user,
            registration,
            decisions,
            occurredAt);
        String localProof = verificationService.issue(
            registration,
            VerificationPurposeEnum.REGISTRATION_EMAIL,
            occurredAt).getToken();
        String googleProof = verificationService.issue(
            registration,
            VerificationPurposeEnum.EXTERNAL_REGISTRATION,
            occurredAt).getToken();
        return new ActivationRaceFixture(
            terms.getId(),
            privacy.getId(),
            localProof,
            googleProof);
      });

      CountDownLatch start = new CountDownLatch(1);
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        Future<RegistrationActivationResultVO> local = executor.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          return transaction(context).execute(status -> localService.activate(
              fixture.localProof(),
              java.util.UUID.randomUUID(),
              occurredAt.plusSeconds(60)));
        });
        Future<ExternalRegistrationCompletionResultVO> google = executor.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          return transaction(context).execute(status -> googleService.complete(
              fixture.googleProof(),
              List.of(fixture.termsId(), fixture.privacyId()),
              java.util.UUID.randomUUID(),
              occurredAt.plusSeconds(60)));
        });
        start.countDown();

        RegistrationActivationResultVO localResult =
            local.get(10, TimeUnit.SECONDS);
        ExternalRegistrationCompletionResultVO googleResult =
            google.get(10, TimeUnit.SECONDS);
        long winners = java.util.stream.Stream.of(
            localResult.status() == RegistrationActivationStatusEnum.ACTIVATED,
            googleResult.status()
                == ExternalRegistrationCompletionStatusEnum.AUTHENTICATED)
            .filter(Boolean::booleanValue)
            .count();

        assertThat(winners).isEqualTo(1);
        assertThat(userRepository.findAll().getFirst().getStatus())
            .isEqualTo(UserStatusEnum.ACTIVE);
        assertThat(registrationRepository.findAll().getFirst().getStatus())
            .isEqualTo(RegistrationStatusEnum.ACTIVE);
        if (googleResult.status()
            == ExternalRegistrationCompletionStatusEnum.AUTHENTICATED) {
          assertThat(credentialRepository.count()).isZero();
          assertThat(externalRepository.findAll().getFirst().getStatus())
              .isEqualTo(ExternalIdentityStatusEnum.ACTIVE);
        } else {
          assertThat(credentialRepository.count()).isEqualTo(1);
          assertThat(externalRepository.count()).isZero();
        }
      } finally {
        executor.shutdownNow();
      }
    });
  }

  /**
   * Comprova a unicidade física da decisão por usuário e versão legal.
   */
  @Test
  void save_shouldRejectDuplicateConsent_whenUserAlreadyDecidedDocumentVersion() {
    contextRunner().run(context -> {
      LegalConsentRepository consentRepository = context.getBean(LegalConsentRepository.class);
      TransactionTemplate transaction = transaction(context);
      LegalPersistenceFixture fixture = transaction.execute(
          status -> createLegalFixture(context, "user@example.com"));

      assertThatThrownBy(() -> transaction.executeWithoutResult(
          status -> consentRepository.saveAndFlush(new LegalConsentEntity(
              fixture.user(),
              fixture.registration(),
              fixture.document(),
              LegalConsentDecisionEnum.ACCEPTED,
              Instant.parse("2026-07-29T18:30:00Z")))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(consentRepository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova que expirar a identidade pendente elimina suas evidências temporárias.
   */
  @Test
  void delete_shouldRemovePendingConsent_whenPendingUserExpires() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LegalConsentRepository consentRepository = context.getBean(LegalConsentRepository.class);
      LegalDocumentVersionRepository documentRepository =
          context.getBean(LegalDocumentVersionRepository.class);
      TransactionTemplate transaction = transaction(context);
      LegalPersistenceFixture fixture = transaction.execute(
          status -> createLegalFixture(context, "expired@example.com"));

      transaction.executeWithoutResult(status -> {
        userRepository.deleteById(fixture.user().getId());
        userRepository.flush();
      });

      assertThat(userRepository.findById(fixture.user().getId())).isEmpty();
      assertThat(registrationRepository.findById(fixture.registration().getId())).isEmpty();
      assertThat(consentRepository.count()).isZero();
      assertThat(documentRepository.findById(fixture.document().getId())).isPresent();
    });
  }

  /**
   * Comprova a minimização da origem sem apagar a evidência de usuário preservado.
   */
  @Test
  void delete_shouldNullRegistrationReference_whenOnlyTemporaryRegistrationIsRemoved() {
    contextRunner().run(context -> {
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      LegalConsentRepository consentRepository = context.getBean(LegalConsentRepository.class);
      TransactionTemplate transaction = transaction(context);
      LegalPersistenceFixture fixture = transaction.execute(
          status -> createLegalFixture(context, "active@example.com"));

      transaction.executeWithoutResult(status -> {
        registrationRepository.deleteById(fixture.registration().getId());
        registrationRepository.flush();
      });

      Boolean registrationWasMinimized = transaction.execute(status -> consentRepository
          .findAll()
          .getFirst()
          .getRegistration() == null);
      assertThat(registrationWasMinimized).isTrue();
      assertThat(consentRepository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova que issuer e subject identificam um único vínculo, mesmo entre usuários.
   */
  @Test
  void save_shouldRejectDuplicateExternalIdentity_whenIssuerAndSubjectAlreadyExist() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      TransactionTemplate transaction = transaction(context);
      UserEntity firstUser = transaction.execute(
          status -> userRepository.saveAndFlush(new UserEntity(
              "first@example.com",
              "first@example.com",
              UserStatusEnum.ACTIVE)));
      UserEntity secondUser = transaction.execute(
          status -> userRepository.saveAndFlush(new UserEntity(
              "second@example.com",
              "second@example.com",
              UserStatusEnum.ACTIVE)));
      transaction.executeWithoutResult(status -> externalRepository.saveAndFlush(
          externalIdentity(firstUser)));

      assertThatThrownBy(() -> transaction.executeWithoutResult(
          status -> externalRepository.saveAndFlush(externalIdentity(secondUser))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(externalRepository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova que issuer e subject escolhem um único vencedor sob disputa real.
   *
   * @throws Exception quando a coordenação local das escritas falha
   */
  @Test
  void save_shouldChooseOneExternalIdentity_whenProviderKeyIsWrittenConcurrently()
      throws Exception {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      ExternalIdentityRepository externalRepository =
          context.getBean(ExternalIdentityRepository.class);
      TransactionTemplate setupTransaction = transaction(context);
      UserEntity firstUser = setupTransaction.execute(
          status -> userRepository.saveAndFlush(new UserEntity(
              "first@example.com",
              "first@example.com",
              UserStatusEnum.ACTIVE)));
      UserEntity secondUser = setupTransaction.execute(
          status -> userRepository.saveAndFlush(new UserEntity(
              "second@example.com",
              "second@example.com",
              UserStatusEnum.ACTIVE)));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<Boolean> first = executor.submit(() -> saveAfterSignal(
            start,
            transaction(context),
            () -> externalRepository.saveAndFlush(externalIdentity(firstUser))));
        Future<Boolean> second = executor.submit(() -> saveAfterSignal(
            start,
            transaction(context),
            () -> externalRepository.saveAndFlush(externalIdentity(secondUser))));
        start.countDown();

        assertThat(List.of(
            first.get(10, TimeUnit.SECONDS),
            second.get(10, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(true, false);
        assertThat(externalRepository.count()).isEqualTo(1);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  /**
   * Comprova que IPv4/IPv6 são persistidos canonicamente e expiram pela janela, não pelo IP.
   */
  @Test
  void findByWindowEndsAtBefore_shouldSelectOnlyOriginWindowsOutsideRetention() {
    contextRunner().run(context -> {
      OriginWindowRepository repository = context.getBean(OriginWindowRepository.class);
      OriginAddressService addressService = new OriginAddressService();
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> {
        repository.save(new OriginWindowEntity(
            addressService.normalize("192.0.2.10").getAddress(),
            OriginOperationEnum.USER_REGISTRATION,
            OriginPolicyEnum.ABSOLUTE_LIMIT,
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-06-02T00:00:00Z")));
        repository.saveAndFlush(new OriginWindowEntity(
            addressService.normalize("2001:db8::1").getAddress(),
            OriginOperationEnum.USER_REGISTRATION,
            OriginPolicyEnum.TURNSTILE_THRESHOLD,
            Instant.parse("2026-07-28T00:00:00Z"),
            Instant.parse("2026-07-29T00:00:00Z")));
      });

      List<OriginWindowEntity> expired = repository.findByWindowEndsAtBefore(
          Instant.parse("2026-07-01T00:00:00Z"));

      assertThat(expired).hasSize(1);
      assertThat(expired.getFirst().getOriginAddress()).hasSize(4);
      assertThat(repository.findAll())
          .extracting(window -> window.getOriginAddress().length)
          .containsExactlyInAnyOrder(4, 16);
      Integer deleted = transaction.execute(status -> repository.deleteRetentionBatch(
          Instant.parse("2026-07-01T00:00:00Z"),
          500));
      assertThat(deleted).isEqualTo(1);
      assertThat(repository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova o bloqueio exato da vigésima primeira criação sob o mesmo NAT.
   */
  @Test
  void reserveNewRegistration_shouldBlockTwentyFirstCreation_forSharedOrigin() {
    contextRunner().run(context -> {
      OriginWindowRepository repository = context.getBean(OriginWindowRepository.class);
      OriginLimitService service = new OriginLimitService(
          repository,
          new OriginPropertiesConfig(
              0,
              20,
              Duration.ofHours(24),
              Duration.ofDays(30)));
      OriginAddressVO origin = new OriginAddressService().normalize("192.0.2.10");
      TransactionTemplate transaction = transaction(context);

      for (int index = 0; index < 20; index++) {
        OriginReservationStatusEnum reservation = transaction.execute(
            status -> service.reserveNewRegistration(
                origin,
                OriginOperationEnum.USER_REGISTRATION).status());
        assertThat(reservation)
            .isEqualTo(OriginReservationStatusEnum.RESERVED);
      }
      OriginReservationStatusEnum blocked = transaction.execute(
          status -> service.reserveNewRegistration(
              origin,
              OriginOperationEnum.USER_REGISTRATION).status());
      assertThat(blocked)
          .isEqualTo(OriginReservationStatusEnum.BLOCKED);

      OriginWindowEntity persisted = repository.findAll().getFirst();
      assertThat(persisted.getEventCount()).isEqualTo(20);
      assertThat(persisted.getActiveMarker()).isTrue();
      assertThat(repository.count()).isEqualTo(1);

      OriginAddressVO otherOrigin = new OriginAddressService().normalize("192.0.2.11");
      OriginReservationStatusEnum otherOriginReservation = transaction.execute(
          status -> service.reserveNewRegistration(
              otherOrigin,
              OriginOperationEnum.USER_REGISTRATION).status());

      assertThat(otherOriginReservation).isEqualTo(OriginReservationStatusEnum.RESERVED);
      assertThat(repository.count()).isEqualTo(2);
    });
  }

  /**
   * Comprova que duas instâncias não criam janelas ativas distintas para uma origem nova.
   *
   * @throws Exception quando a coordenação local das reservas falha
   */
  @Test
  void reserveNewRegistration_shouldChooseOneWinner_whenFirstWindowIsConcurrent()
      throws Exception {
    contextRunner().run(context -> {
      OriginWindowRepository repository = context.getBean(OriginWindowRepository.class);
      OriginLimitService service = new OriginLimitService(
          repository,
          new OriginPropertiesConfig(
              0,
              1,
              Duration.ofHours(24),
              Duration.ofDays(30)));
      OriginAddressVO origin = new OriginAddressService().normalize("2001:db8::10");
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<OriginReservationStatusEnum> first = executor.submit(() -> reserveAfterSignal(
            start,
            transaction(context),
            service,
            origin));
        Future<OriginReservationStatusEnum> second = executor.submit(() -> reserveAfterSignal(
            start,
            transaction(context),
            service,
            origin));
        start.countDown();

        assertThat(List.of(
            first.get(10, TimeUnit.SECONDS),
            second.get(10, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(
                OriginReservationStatusEnum.RESERVED,
                OriginReservationStatusEnum.BLOCKED);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findAll().getFirst().getEventCount()).isEqualTo(1);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  /**
   * Comprova que o fim da janela libera automaticamente uma nova reserva e preserva o histórico.
   */
  @Test
  void reserveNewRegistration_shouldOpenNewWindow_whenPreviousWindowExpired() {
    contextRunner().run(context -> {
      OriginWindowRepository repository = context.getBean(OriginWindowRepository.class);
      OriginAddressVO origin = new OriginAddressService().normalize("192.0.2.10");
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> repository.saveAndFlush(
          new OriginWindowEntity(
              origin.getAddress(),
              OriginOperationEnum.USER_REGISTRATION,
              OriginPolicyEnum.ABSOLUTE_LIMIT,
              Instant.parse("2020-01-01T00:00:00Z"),
              Instant.parse("2020-01-02T00:00:00Z"))));
      OriginLimitService service = new OriginLimitService(
          repository,
          new OriginPropertiesConfig(
              0,
              1,
              Duration.ofHours(24),
              Duration.ofDays(30)));

      OriginReservationStatusEnum result = transaction.execute(
          status -> service.reserveNewRegistration(
              origin,
              OriginOperationEnum.USER_REGISTRATION).status());

      assertThat(result).isEqualTo(OriginReservationStatusEnum.RESERVED);
      assertThat(repository.findAll())
          .extracting(OriginWindowEntity::getActiveMarker)
          .containsExactlyInAnyOrder(null, true);
    });
  }

  /**
   * Comprova que apagar a identidade minimiza as FKs sem remover o evento permanente.
   */
  @Test
  void deleteUser_shouldPreserveIdentityEventWithNullReferences() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      Long eventId = transaction.execute(status -> {
        UserEntity persistedUser = userRepository.saveAndFlush(user("event@example.com"));
        RegistrationEntity persistedRegistration = registrationRepository.saveAndFlush(
            registration(persistedUser, RegistrationMethodEnum.LOCAL));
        IdentityEventEntity event = eventRepository.saveAndFlush(new IdentityEventEntity(
            persistedUser,
            persistedRegistration,
            new byte[16],
            IdentityEventTypeEnum.REGISTRATION_STARTED,
            null,
            null,
            IdentityTransitionOriginEnum.SELF_SERVICE,
            "USER_REQUEST",
            Instant.parse("2026-07-29T18:00:00Z")));
        return event.getId();
      });

      transaction.executeWithoutResult(status -> {
        UserEntity persistedUser = userRepository.findByNormalizedEmail("shared@example.com")
            .orElseThrow();
        userRepository.delete(persistedUser);
        userRepository.flush();
      });

      Boolean referencesWereMinimized = transaction.execute(status -> {
        IdentityEventEntity event = eventRepository.findById(eventId).orElseThrow();
        return event.getUser() == null && event.getRegistration() == null;
      });
      assertThat(referencesWereMinimized).isTrue();
      assertThat(eventRepository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova que a seleção bloqueável nunca inclui identidade já ativada.
   */
  @Test
  void findExpiredPendingBatchForUpdate_shouldSelectOnlyStillPendingUsers() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> {
        UserEntity pendingUser = userRepository.saveAndFlush(new UserEntity(
            "pending-expired@example.com",
            "pending-expired@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        registrationRepository.saveAndFlush(new RegistrationEntity(
            pendingUser,
            RegistrationMethodEnum.LOCAL,
            RegistrationStatusEnum.PENDING_VERIFICATION,
            Instant.parse("2026-07-28T12:00:00Z")));
        UserEntity activeUser = userRepository.saveAndFlush(new UserEntity(
            "active-expired@example.com",
            "active-expired@example.com",
            UserStatusEnum.ACTIVE));
        registrationRepository.saveAndFlush(new RegistrationEntity(
            activeUser,
            RegistrationMethodEnum.LOCAL,
            RegistrationStatusEnum.PENDING_VERIFICATION,
            Instant.parse("2026-07-28T12:00:00Z")));
      });

      List<RegistrationEntity> selected = transaction.execute(
          status -> registrationRepository.findExpiredPendingBatchForUpdate(
              RegistrationStatusEnum.PENDING_VERIFICATION,
              UserStatusEnum.PENDING_VERIFICATION,
              Instant.parse("2026-07-29T12:00:00Z"),
              PageRequest.of(0, 10)));

      assertThat(selected).hasSize(1);
      assertThat(selected.getFirst().getUser().getNormalizedEmail())
          .isEqualTo("pending-expired@example.com");
    });
  }

  /**
   * Comprova que a limpeza coordenada remove de fato a raiz vencida no MySQL e preserva tombstone sem PII.
   */
  @Test
  void expiryCleanup_shouldDeleteExpiredRootAndPersistPiiFreeTombstone() {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> {
        UserEntity user = userRepository.saveAndFlush(new UserEntity(
            "expired-cleanup@example.com",
            "expired-cleanup@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        registrationRepository.saveAndFlush(new RegistrationEntity(
            user,
            RegistrationMethodEnum.LOCAL,
            RegistrationStatusEnum.PENDING_VERIFICATION,
            Instant.parse("2026-07-28T12:00:00Z")));
      });
      MaintenanceCoordinatorService coordinator = mock(MaintenanceCoordinatorService.class);
      when(coordinator.canStartJob()).thenReturn(true);
      when(coordinator.executeBatch(any(Runnable.class))).thenAnswer(invocation -> {
        Runnable batch = invocation.getArgument(0);
        transaction.executeWithoutResult(status -> batch.run());
        return true;
      });
      RegistrationExpiryCleanupService cleanupService = new RegistrationExpiryCleanupService(
          registrationRepository,
          userRepository,
          new RegistrationLifecycleService(),
          new IdentityAuditService(eventRepository),
          coordinator,
          new CleanupPropertiesConfig(Duration.ofHours(24), 10),
          mock(RegistrationObservabilityService.class));

      int deleted = cleanupService.cleanup(Instant.parse("2026-07-29T12:00:00Z"));

      assertThat(deleted).isEqualTo(1);
      assertThat(userRepository.count()).isZero();
      assertThat(registrationRepository.count()).isZero();
      assertThat(eventRepository.findAll())
          .singleElement()
          .satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(IdentityEventTypeEnum.REGISTRATION_EXPIRED);
            assertThat(event.getUser()).isNull();
            assertThat(event.getRegistration()).isNull();
          });
    });
  }

  /**
   * Comprova que a retenção exclui somente tombstone minimizado e vencido.
   */
  @Test
  void deleteTombstoneBatch_shouldPreserveRecentAndRelatedEvents() {
    contextRunner().run(context -> {
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> {
        eventRepository.save(new IdentityEventEntity(
            null,
            null,
            new byte[16],
            IdentityEventTypeEnum.REGISTRATION_CANCELLED,
            null,
            null,
            IdentityTransitionOriginEnum.SELF_SERVICE,
            "EMAIL_CONTROL_CONFIRMED",
            Instant.parse("2026-07-01T12:00:00Z")));
        eventRepository.saveAndFlush(new IdentityEventEntity(
            null,
            null,
            new byte[16],
            IdentityEventTypeEnum.REGISTRATION_CANCELLED,
            null,
            null,
            IdentityTransitionOriginEnum.SELF_SERVICE,
            "EMAIL_CONTROL_CONFIRMED",
            Instant.parse("2026-07-20T12:00:00Z")));
      });

      Integer deleted = transaction.execute(status -> eventRepository.deleteTombstoneBatch(
          IdentityEventTypeEnum.REGISTRATION_CANCELLED.name(),
          Instant.parse("2026-07-14T12:00:00Z"),
          500));

      assertThat(deleted).isEqualTo(1);
      assertThat(eventRepository.count()).isEqualTo(1);
      assertThat(eventRepository.findAll().getFirst().getOccurredAt())
          .isEqualTo(Instant.parse("2026-07-20T12:00:00Z"));
    });
  }

  /**
   * Comprova que duas confirmações concorrentes produzem uma única remoção e liberam o e-mail.
   *
   * @throws Exception quando a coordenação local das confirmações falha
   */
  @Test
  void cancelRegistration_shouldChooseOneWinner_andReleaseEmailForNewRegistration()
      throws Exception {
    contextRunner().run(context -> {
      UserRepository userRepository = context.getBean(UserRepository.class);
      RegistrationRepository registrationRepository =
          context.getBean(RegistrationRepository.class);
      VerificationRepository verificationRepository =
          context.getBean(VerificationRepository.class);
      IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
      VerificationTokenService tokenService = new VerificationTokenService();
      String token = tokenService.generate();
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> {
        UserEntity persistedUser = userRepository.saveAndFlush(new UserEntity(
            "cancel@example.com",
            "cancel@example.com",
            UserStatusEnum.PENDING_VERIFICATION));
        RegistrationEntity persistedRegistration = registrationRepository.saveAndFlush(
            registration(persistedUser, RegistrationMethodEnum.LOCAL));
        verificationRepository.saveAndFlush(new VerificationEntity(
            persistedRegistration,
            VerificationPurposeEnum.REGISTRATION_CANCEL,
            tokenService.hash(token),
            Instant.parse("2026-07-29T18:00:00Z"),
            Instant.parse("2026-07-30T18:00:00Z")));
      });
      VerificationService verificationService = new VerificationService(
          verificationRepository,
          registrationRepository,
          tokenService,
          new VerificationPropertiesConfig(Duration.ofHours(24)));
      RegistrationCancellationService cancellationService =
          new RegistrationCancellationService(
              registrationRepository,
              eventRepository,
              userRepository,
              verificationService,
              new EmailNormalizationService(),
              new RegistrationLifecycleService(),
              new UserLifecycleService(),
              new IdentityAuditService(eventRepository),
              mock(PublicApplicationUriService.class),
              mock(VerificationEmailDispatchService.class),
              new RegistrationPropertiesConfig(
                  Duration.ofDays(15),
                  3,
                  Duration.ofMinutes(15),
                  3,
                  Duration.ofMinutes(15)));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      try {
        Future<RegistrationCancellationConfirmationStatusEnum> first =
            executor.submit(() -> cancelAfterSignal(
                start,
                transaction(context),
                cancellationService,
                token));
        Future<RegistrationCancellationConfirmationStatusEnum> second =
            executor.submit(() -> cancelAfterSignal(
                start,
                transaction(context),
                cancellationService,
                token));
        start.countDown();

        assertThat(List.of(
            first.get(10, TimeUnit.SECONDS),
            second.get(10, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(
                RegistrationCancellationConfirmationStatusEnum.CANCELLED,
                RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF);
      } finally {
        executor.shutdownNow();
      }

      assertThat(userRepository.count()).isZero();
      assertThat(registrationRepository.count()).isZero();
      assertThat(verificationRepository.count()).isZero();
      assertThat(eventRepository
          .findByEventTypeAndUserIsNullAndRegistrationIsNullAndOccurredAtBefore(
              IdentityEventTypeEnum.REGISTRATION_CANCELLED,
              Instant.parse("2026-07-30T00:00:00Z")))
          .hasSize(1);

      transaction.executeWithoutResult(status -> userRepository.saveAndFlush(new UserEntity(
          "cancel@example.com",
          "cancel@example.com",
          UserStatusEnum.PENDING_VERIFICATION)));
      assertThat(userRepository.count()).isEqualTo(1);
    });
  }

  /**
   * Comprova que todos os mappings JPA validam contra o init físico do MySQL 9.
   */
  @Test
  void startup_shouldValidateAllIdentityMappingsAgainstPhysicalSchema() {
    contextRunner().run(context -> assertThat(context).hasNotFailed());
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class))
        .withUserConfiguration(RepositoryTestConfig.class)
        .withPropertyValues(
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.hibernate.naming.physical-strategy="
                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
        .withBean(DataSource.class, () -> dataSource);
  }

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  private static GoogleIdentityResolutionService googleResolutionService(
      ApplicationContext context) {
    UserRepository userRepository = context.getBean(UserRepository.class);
    RegistrationRepository registrationRepository =
        context.getBean(RegistrationRepository.class);
    ExternalIdentityRepository externalRepository =
        context.getBean(ExternalIdentityRepository.class);
    VerificationRepository verificationRepository =
        context.getBean(VerificationRepository.class);
    IdentityEventRepository eventRepository = context.getBean(IdentityEventRepository.class);
    EmailNormalizationService normalizationService = new EmailNormalizationService();
    return new GoogleIdentityResolutionService(
        new IdentityService(userRepository, registrationRepository, normalizationService),
        normalizationService,
        new ExternalIdentityService(externalRepository),
        registrationRepository,
        new VerificationService(
            verificationRepository,
            registrationRepository,
            new VerificationTokenService(),
            new VerificationPropertiesConfig(Duration.ofHours(24))),
        new IdentityAuditService(eventRepository),
        new RegistrationPropertiesConfig(
            Duration.ofDays(15),
            3,
            Duration.ofMinutes(15),
            3,
            Duration.ofMinutes(15)));
  }

  private static RegistrationAuthenticationContinuationService registrationContinuationService() {
    RegistrationAuthenticationContinuationService service =
        mock(RegistrationAuthenticationContinuationService.class);
    when(service.issue(any(), any(), any(), any())).thenAnswer(invocation -> {
      UserEntity user = invocation.getArgument(0);
      return new br.com.rinos.app.api.vo.RegistrationAuthenticationContinuationVO(
          new br.com.rinos.app.api.vo.RinosUserPrincipalVO(user.getId(), user.getEmail()),
          new br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO(
              "registration-test-continuation",
              br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum.REGISTRATION_ACTIVATION));
    });
    return service;
  }

  private static UserLifecycleService lifecycleWithPersonalContract() {
    PersonalContractBootstrapPort contracts = request -> new ContractBootstrapResult(
        ContractBootstrapStatus.ALREADY_COMPLETED,
        ContractScope.PERSONAL,
        java.util.UUID.randomUUID(),
        null);
    return new UserLifecycleService(mock(AuthSessionService.class), contracts);
  }

  private static UserEntity user(String email) {
    return new UserEntity(
        email,
        "shared@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
  }

  private static RegistrationEntity registration(
      UserEntity user,
      RegistrationMethodEnum method) {
    return new RegistrationEntity(
        user,
        method,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        Instant.parse("2026-08-13T18:00:00Z"));
  }

  private static ExternalIdentityEntity externalIdentity(UserEntity user) {
    return new ExternalIdentityEntity(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "shared-subject",
        Instant.parse("2026-07-29T18:00:00Z"));
  }

  private static LegalDocumentVersionEntity legalDocument(
      LegalDocumentTypeEnum type,
      String versionName,
      Instant effectiveFrom) {
    String content = "Documento " + versionName;
    return new LegalDocumentVersionEntity(
        type,
        versionName,
        true,
        content,
        new LegalDocumentIntegrityService().hash(content),
        effectiveFrom,
        null);
  }

  /**
   * Persiste uma identidade, cadastro, versão legal e aceite relacionados.
   *
   * @param context contexto Spring do teste
   * @param email chave exclusiva do cenário
   * @return fixture persistida
   */
  private static LegalPersistenceFixture createLegalFixture(
      ApplicationContext context,
      String email) {
    UserRepository userRepository = context.getBean(UserRepository.class);
    RegistrationRepository registrationRepository =
        context.getBean(RegistrationRepository.class);
    LegalDocumentVersionRepository documentRepository =
        context.getBean(LegalDocumentVersionRepository.class);
    LegalConsentRepository consentRepository = context.getBean(LegalConsentRepository.class);
    UserEntity persistedUser = userRepository.saveAndFlush(new UserEntity(
        email,
        email,
        UserStatusEnum.PENDING_VERIFICATION));
    RegistrationEntity persistedRegistration = registrationRepository.saveAndFlush(
        registration(persistedUser, RegistrationMethodEnum.LOCAL));
    String content = "Termos para " + email;
    LegalDocumentVersionEntity persistedDocument = documentRepository.saveAndFlush(
        new LegalDocumentVersionEntity(
            LegalDocumentTypeEnum.TERMS_OF_USE,
            email,
            true,
            content,
            new LegalDocumentIntegrityService().hash(content),
            Instant.parse("2026-07-29T17:00:00Z"),
            null));
    consentRepository.saveAndFlush(new LegalConsentEntity(
        persistedUser,
        persistedRegistration,
        persistedDocument,
        LegalConsentDecisionEnum.ACCEPTED,
        Instant.parse("2026-07-29T18:00:00Z")));
    return new LegalPersistenceFixture(
        persistedUser,
        persistedRegistration,
        persistedDocument);
  }

  /**
   * Aguarda o disparo comum e consome a prova dentro de transação própria.
   *
   * @param start barreira dos concorrentes
   * @param transaction fronteira transacional independente
   * @param service serviço exercitado
   * @param registrationId cadastro proprietário
   * @param token prova compartilhada pelos concorrentes
   * @return resultado de um consumidor
   * @throws InterruptedException quando a espera local é interrompida
   */
  private static VerificationConsumptionStatusEnum consumeAfterSignal(
      CountDownLatch start,
      TransactionTemplate transaction,
      VerificationService service,
      Long registrationId,
      String token) throws InterruptedException {
    start.await(10, TimeUnit.SECONDS);
    return transaction.execute(status -> service.consume(
        registrationId,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        token,
        Instant.parse("2026-07-29T18:30:00Z")));
  }

  /**
   * Dispara uma escrita em transação independente após a barreira concorrente.
   *
   * @param start barreira compartilhada
   * @param transaction fronteira transacional
   * @param persistence escrita que disputa uma constraint
   * @return {@code true} para o vencedor; {@code false} para constraint rejeitada
   * @throws InterruptedException quando a espera local é interrompida
   */
  private static boolean saveAfterSignal(
      CountDownLatch start,
      TransactionTemplate transaction,
      Runnable persistence) throws InterruptedException {
    start.await(10, TimeUnit.SECONDS);
    try {
      transaction.executeWithoutResult(status -> persistence.run());
      return true;
    } catch (DataIntegrityViolationException exception) {
      return false;
    }
  }

  private static OriginReservationStatusEnum reserveAfterSignal(
      CountDownLatch start,
      TransactionTemplate transaction,
      OriginLimitService service,
      OriginAddressVO origin)
      throws InterruptedException {
    start.await(10, TimeUnit.SECONDS);
    return transaction.execute(status -> service.reserveNewRegistration(
        origin,
        OriginOperationEnum.USER_REGISTRATION).status());
  }

  private static RegistrationCancellationConfirmationStatusEnum cancelAfterSignal(
      CountDownLatch start,
      TransactionTemplate transaction,
      RegistrationCancellationService service,
      String token) throws InterruptedException {
    start.await(10, TimeUnit.SECONDS);
    return transaction.execute(status -> service.confirm(
        "cancel@example.com",
        token,
        java.util.UUID.randomUUID(),
        Instant.parse("2026-07-29T18:30:00Z")));
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = UserEntity.class)
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  static class RepositoryTestConfig {
  }

  private record LegalPersistenceFixture(
      UserEntity user,
      RegistrationEntity registration,
      LegalDocumentVersionEntity document) {
  }

  private record ExternalRegistrationFixture(
      Long userId,
      Long registrationId,
      Long termsId,
      Long privacyId,
      String proof) {
  }

  private record ActivationRaceFixture(
      Long termsId,
      Long privacyId,
      String localProof,
      String googleProof) {
  }
}
