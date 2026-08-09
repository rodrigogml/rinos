package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.entity.LegalDocumentVersionEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalDocumentTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAssurancePolicyService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationFlowService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationOrchestrationService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationProofService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

/**
 * Comprova no MySQL que locks pessimistas escolhem um único consumidor de fluxo e prova.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@DisplayName("Concorrência persistente dos fluxos de autenticação")
class AuthenticationFlowRepositoryIT {

  private static final Instant ISSUED_AT = Instant.parse("2026-08-08T12:00:00Z");
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
  void resetDatabase() {
    Assumptions.assumeTrue(
        testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql")).execute(dataSource);
  }

  @Test
  void consumers_shouldChooseOneWinnerForProofAndFlow() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository userRepository = context.getBean(UserRepository.class);
      RFWOpaqueTokenService tokenService = new RFWOpaqueTokenService();
      IdentityAuditService auditService = new IdentityAuditService(
          context.getBean(IdentityEventRepository.class));
      AuthenticationFlowService flowService = new AuthenticationFlowService(
          context.getBean(AuthenticationFlowRepository.class),
          context.getBean(AuthenticationFlowMethodRepository.class),
          context.getBean(AuthenticationProofRepository.class),
          userRepository,
          tokenService,
          auditService);
      AuthenticationProofService proofService = new AuthenticationProofService(
          context.getBean(AuthenticationFlowRepository.class),
          context.getBean(AuthenticationProofRepository.class),
          tokenService,
          auditService);

      Long userId = transaction.execute(status -> userRepository.saveAndFlush(new UserEntity(
          "concurrency@example.test",
          "concurrency@example.test",
          UserStatusEnum.ACTIVE)).getId());
      IssuedAuthenticationFlowVO issued = transaction.execute(status -> flowService.issue(
          userId,
          AuthenticationFlowPurposeEnum.SIGN_IN,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.MULTI_FACTOR,
          Set.of(AuthenticationMethodEnum.EMAIL_CODE),
          false,
          ISSUED_AT,
          ISSUED_AT.plusSeconds(300),
          UUID.fromString("9ab4e7ef-3270-4d16-bc87-13c4a551b2fe")));
      byte[] digest = new byte[] {4, 5, 6, 7};
      transaction.executeWithoutResult(status -> proofService.issue(
          issued.reference(),
          AuthenticationFlowPurposeEnum.SIGN_IN,
          AuthenticationProofTypeEnum.EMAIL_OTP,
          digest,
          null,
          ISSUED_AT.plusSeconds(10),
          ISSUED_AT.plusSeconds(120)));

      List<AuthenticationOperationStatusEnum> proofResults = compete(
          () -> transaction(context).execute(status -> proofService.consume(
              issued.reference(),
              AuthenticationFlowPurposeEnum.SIGN_IN,
              AuthenticationProofTypeEnum.EMAIL_OTP,
              digest,
              ISSUED_AT.plusSeconds(30)).status()));
      List<AuthenticationOperationStatusEnum> flowResults = compete(
          () -> transaction(context).execute(status -> flowService.consume(
              issued.reference(),
              AuthenticationFlowPurposeEnum.SIGN_IN,
              ISSUED_AT.plusSeconds(40)).status()));

      assertThat(proofResults).containsExactlyInAnyOrder(
          AuthenticationOperationStatusEnum.USED,
          AuthenticationOperationStatusEnum.ALREADY_USED);
      assertThat(flowResults).containsExactlyInAnyOrder(
          AuthenticationOperationStatusEnum.USED,
          AuthenticationOperationStatusEnum.ALREADY_USED);
    });
  }

  @Test
  void legalContinuation_shouldPersistOnlyDigestAndPreserveAuthenticationSnapshot() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository userRepository = context.getBean(UserRepository.class);
      AuthenticationFlowRepository flowRepository =
          context.getBean(AuthenticationFlowRepository.class);
      RFWOpaqueTokenService tokenService = new RFWOpaqueTokenService();
      AuthenticationFlowService flowService = new AuthenticationFlowService(
          flowRepository,
          context.getBean(AuthenticationFlowMethodRepository.class),
          context.getBean(AuthenticationProofRepository.class),
          userRepository,
          tokenService,
          new IdentityAuditService(context.getBean(IdentityEventRepository.class)));
      UUID correlationId = UUID.fromString("2043560e-2bd6-4dbb-977f-f6f5a230402a");
      Long userId = transaction.execute(status -> userRepository.saveAndFlush(new UserEntity(
          "legal-continuation@example.test",
          "legal-continuation@example.test",
          UserStatusEnum.ACTIVE)).getId());

      IssuedAuthenticationFlowVO issued = transaction.execute(status -> flowService.issue(
          userId,
          AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.SINGLE_FACTOR,
          Set.of(),
          List.of(new AuthenticationFlowVerifiedMethodVO(
              AuthenticationMethodEnum.PASSWORD, ISSUED_AT, null)),
          true,
          ISSUED_AT,
          ISSUED_AT.plusSeconds(300),
          correlationId));
      AuthenticationFlowSnapshotVO snapshot = transaction.execute(status -> flowService.snapshot(
          issued.reference(), AuthenticationFlowPurposeEnum.LEGAL_CONSENT, ISSUED_AT));

      assertThat(issued.reference()).isNotBlank();
      assertThat(flowRepository.findAll())
          .singleElement()
          .satisfies(flow -> assertThat(flow.getReferenceHash())
              .containsExactly(tokenService.hash(issued.reference())));
      assertThat(snapshot.status()).isEqualTo(AuthenticationOperationStatusEnum.OPEN);
      assertThat(snapshot.userId()).isEqualTo(userId);
      assertThat(snapshot.purpose()).isEqualTo(AuthenticationFlowPurposeEnum.LEGAL_CONSENT);
      assertThat(snapshot.primaryMethod()).isEqualTo(AuthenticationMethodEnum.PASSWORD);
      assertThat(snapshot.requiredAssurance())
          .isEqualTo(AuthenticationAssuranceEnum.SINGLE_FACTOR);
      assertThat(snapshot.verifiedMethods())
          .extracting(AuthenticationFlowVerifiedMethodVO::method)
          .containsExactly(AuthenticationMethodEnum.PASSWORD);
      assertThat(snapshot.persistentLoginRequested()).isTrue();
      assertThat(snapshot.expiresAt()).isEqualTo(ISSUED_AT.plusSeconds(300));
      assertThat(snapshot.correlationId()).isEqualTo(correlationId);
    });
  }

  @Test
  void legalCompletion_shouldRecordConsentsAndConsumeMarkerWhileFlowRemainsOpen() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository users = context.getBean(UserRepository.class);
      AuthenticationFlowRepository flowsRepository =
          context.getBean(AuthenticationFlowRepository.class);
      AuthenticationProofRepository proofsRepository =
          context.getBean(AuthenticationProofRepository.class);
      RFWOpaqueTokenService tokens = new RFWOpaqueTokenService();
      IdentityAuditService audit = new IdentityAuditService(
          context.getBean(IdentityEventRepository.class));
      AuthenticationFlowService flows = new AuthenticationFlowService(
          flowsRepository,
          context.getBean(AuthenticationFlowMethodRepository.class),
          proofsRepository,
          users,
          tokens,
          audit);
      AuthenticationProofService proofs = new AuthenticationProofService(
          flowsRepository, proofsRepository, tokens, audit);
      LegalConsentService legal = new LegalConsentService(
          context.getBean(LegalDocumentVersionRepository.class),
          context.getBean(LegalConsentRepository.class));
      AuthenticationMethodAvailabilityService availability =
          mock(AuthenticationMethodAvailabilityService.class);
      AuthenticationOrchestrationService orchestration = new AuthenticationOrchestrationService(
          flows,
          proofs,
          new AuthenticationAssurancePolicyService(),
          availability,
          legal,
          users);
      Long userId = transaction.execute(status -> users.saveAndFlush(new UserEntity(
          "legal-completion@example.test",
          "legal-completion@example.test",
          UserStatusEnum.ACTIVE)).getId());
      when(availability.availableMethods(userId))
          .thenReturn(Set.of(AuthenticationMethodEnum.PASSWORD));
      List<Long> documentIds = transaction.execute(status -> {
        LegalDocumentVersionRepository documents =
            context.getBean(LegalDocumentVersionRepository.class);
        return documents.saveAllAndFlush(List.of(
            legalDocument(LegalDocumentTypeEnum.TERMS_OF_USE, "terms-1"),
            legalDocument(LegalDocumentTypeEnum.PRIVACY_POLICY, "privacy-1")))
            .stream().map(LegalDocumentVersionEntity::getId).toList();
      });
      IssuedAuthenticationFlowVO issued = transaction.execute(status -> flows.issue(
          userId,
          AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.SINGLE_FACTOR,
          Set.of(),
          List.of(new AuthenticationFlowVerifiedMethodVO(
              AuthenticationMethodEnum.PASSWORD, ISSUED_AT, null)),
          false,
          ISSUED_AT,
          ISSUED_AT.plusSeconds(300),
          UUID.randomUUID()));
      transaction.executeWithoutResult(status -> proofs.issue(
          issued.reference(),
          AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
          AuthenticationProofTypeEnum.LEGAL_CONSENT,
          new byte[32],
          null,
          ISSUED_AT,
          ISSUED_AT.plusSeconds(300)));

      var result = transaction.execute(status -> orchestration.completeLegalConsent(
          issued.reference(), Set.copyOf(documentIds), ISSUED_AT.plusSeconds(30)));

      assertThat(result.status())
          .isEqualTo(br.com.rinos.app.backend.module.identity.enums
              .AuthenticationOrchestrationStatusEnum.READY);
      assertThat(context.getBean(LegalConsentRepository.class).findByUserId(userId)).hasSize(2);
      AuthenticationOperationStatusEnum proofStatus = transaction.execute(status -> proofs.inspect(
          issued.reference(),
          AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
          AuthenticationProofTypeEnum.LEGAL_CONSENT,
          ISSUED_AT.plusSeconds(31)).status());
      AuthenticationOperationStatusEnum flowStatus = transaction.execute(status -> flows.inspect(
          issued.reference(),
          AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
          ISSUED_AT.plusSeconds(31)).status());
      assertThat(proofStatus).isEqualTo(AuthenticationOperationStatusEnum.ALREADY_USED);
      assertThat(flowStatus).isEqualTo(AuthenticationOperationStatusEnum.OPEN);
    });
  }

  private static LegalDocumentVersionEntity legalDocument(
      LegalDocumentTypeEnum type,
      String version) {
    return new LegalDocumentVersionEntity(
        type,
        version,
        true,
        "content",
        new byte[32],
        ISSUED_AT.minusSeconds(60),
        null);
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

  private static List<AuthenticationOperationStatusEnum> compete(
      java.util.concurrent.Callable<AuthenticationOperationStatusEnum> operation) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<AuthenticationOperationStatusEnum> first = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      Future<AuthenticationOperationStatusEnum> second = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      start.countDown();
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } catch (Exception exception) {
      throw new IllegalStateException("Concurrent authentication operation failed", exception);
    } finally {
      executor.shutdownNow();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = UserEntity.class)
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  static class RepositoryTestConfig {
  }
}
