package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.identity.entity.EmailFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationFlowService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationKeyringMacService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationKeyringService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationProofService;
import br.com.rinos.app.backend.module.identity.service.EmailOtpService;
import br.com.rinos.app.backend.module.identity.service.EmailPrivacyService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.authentication.service.RFWOneTimeCodeService;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

/** Comprova no MySQL que o mesmo OTP não pode ser consumido por duas instâncias. */
@DisplayName("Consumo concorrente do OTP por e-mail no MySQL")
class EmailOtpRepositoryIT {

  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");
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
    Assumptions.assumeTrue(testDatabase != null, "MySQL de teste indisponível");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql"))
        .execute(dataSource);
  }

  @Test
  void verify_shouldAllowOnlyOneConsumerForSameEmailCode() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      UserRepository users = context.getBean(UserRepository.class);
      EmailFactorRepository factors = context.getBean(EmailFactorRepository.class);
      AuthenticationFlowRepository flowRepository =
          context.getBean(AuthenticationFlowRepository.class);
      AuthenticationProofRepository proofRepository =
          context.getBean(AuthenticationProofRepository.class);
      RFWOpaqueTokenService tokens = new RFWOpaqueTokenService();
      IdentityAuditService audit = new IdentityAuditService(
          context.getBean(IdentityEventRepository.class));
      AuthenticationFlowService flows = new AuthenticationFlowService(
          flowRepository,
          context.getBean(AuthenticationFlowMethodRepository.class),
          proofRepository,
          users,
          tokens,
          audit);
      AuthenticationKeyringMacService keyring = keyring();
      AuthenticationProofService proofs = new AuthenticationProofService(
          flowRepository, proofRepository, tokens, audit, keyring);
      RFWOneTimeCodeService codes = mock(RFWOneTimeCodeService.class);
      when(codes.generate()).thenReturn("123456");
      EmailOtpService service = new EmailOtpService(
          flows,
          proofs,
          proofRepository,
          users,
          factors,
          codes,
          keyring,
          new EmailPrivacyService(),
          properties());

      Long userId = transaction.execute(status -> {
        UserEntity user = users.saveAndFlush(new UserEntity(
            "otp@example.test", "otp@example.test", UserStatusEnum.ACTIVE));
        factors.saveAndFlush(new EmailFactorEntity(
            user,
            UUID.fromString("5e09184d-4610-476b-91c6-35760d431813"),
            NOW.minusSeconds(60)));
        return user.getId();
      });
      IssuedAuthenticationFlowVO flow = transaction.execute(status -> flows.issue(
          userId,
          AuthenticationFlowPurposeEnum.SIGN_IN,
          AuthenticationMethodEnum.PASSWORD,
          AuthenticationAssuranceEnum.MULTI_FACTOR,
          Set.of(AuthenticationMethodEnum.EMAIL_CODE),
          false,
          NOW,
          NOW.plusSeconds(300),
          UUID.fromString("e1db5d63-aeb5-4b5a-a450-1a751988cf2b")));
      transaction.execute(status -> service.issue(flow.reference(), false, NOW));

      List<EmailOtpVerificationStatusEnum> results = compete(() ->
          transaction(context).execute(status -> service.verify(
              flow.reference(), "123456", NOW.plusSeconds(30))));

      assertThat(results).containsExactlyInAnyOrder(
          EmailOtpVerificationStatusEnum.USED,
          EmailOtpVerificationStatusEnum.STALE);
      assertThat(factors.findByUserId(userId).orElseThrow().getLastUsedAt())
          .isEqualTo(NOW.plusSeconds(30));
    });
  }

  private static AuthenticationMfaPropertiesConfig properties() {
    return new AuthenticationMfaPropertiesConfig(
        Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15));
  }

  private static AuthenticationKeyringMacService keyring() {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) 4);
    AuthenticationKeyringService service = new AuthenticationKeyringService(
        new AuthenticationKeyringPropertiesConfig(
            true, "v1", Map.of("v1", Base64.getEncoder().encodeToString(key))));
    return new AuthenticationKeyringMacService(service);
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

  private static List<EmailOtpVerificationStatusEnum> compete(
      java.util.concurrent.Callable<EmailOtpVerificationStatusEnum> operation) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<EmailOtpVerificationStatusEnum> first = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      Future<EmailOtpVerificationStatusEnum> second = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      start.countDown();
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } catch (Exception failure) {
      throw new IllegalStateException("Concurrent e-mail OTP verification failed", failure);
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
