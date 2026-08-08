package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationFlowService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationProofService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
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
