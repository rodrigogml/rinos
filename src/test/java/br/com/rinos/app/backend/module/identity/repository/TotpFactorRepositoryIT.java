package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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

import br.com.rinos.app.backend.module.identity.entity.TotpFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationKeyringService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodInventoryService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.IdentityReferenceService;
import br.com.rinos.app.backend.module.identity.service.TotpFactorService;
import br.com.rinos.app.backend.module.identity.service.TotpProtocolService;
import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.config.RFWAuthenticationPropertiesConfig.SecondFactorConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWTotpService;

/** Comprova no MySQL que duas instâncias não consomem o mesmo passo TOTP. */
@DisplayName("Replay TOTP concorrente no MySQL")
class TotpFactorRepositoryIT {

  private static final Instant VERIFY_AT = Instant.ofEpochSecond(1_111_111_109L);
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
  void verifyActive_shouldAllowOnlyOneConsumerForSameTimeStep() {
    contextRunner().run(context -> {
      UserRepository users = context.getBean(UserRepository.class);
      TotpFactorRepository factors = context.getBean(TotpFactorRepository.class);
      AuthenticationKeyringService keyring = keyring();
      TotpProtocolService protocol = protocol(keyring);
      TotpFactorService service = new TotpFactorService(
          users,
          factors,
          mock(AuthenticationMethodInventoryService.class),
          new IdentityReferenceService(),
          mock(IdentityAuditService.class),
          protocol,
          new AuthenticationMfaPropertiesConfig(
              Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)));
      UUID reference = UUID.fromString("6e89f399-c679-495c-8c42-b17f2db18996");

      Long userId = transaction(context).execute(status -> users.saveAndFlush(new UserEntity(
          "totp@example.test", "totp@example.test", UserStatusEnum.ACTIVE)).getId());
      EncryptedAuthenticationSecretVO encrypted = keyring.encrypt(
          "totp-secret:" + userId + ":" + reference,
          "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ".getBytes(StandardCharsets.US_ASCII));
      transaction(context).executeWithoutResult(status -> {
        UserEntity user = users.findById(userId).orElseThrow();
        TotpFactorEntity factor = new TotpFactorEntity(
            user,
            reference,
            "Aplicativo autenticador",
            encrypted.ciphertext(),
            encrypted.nonce(),
            encrypted.keyVersion(),
            VERIFY_AT.plusSeconds(300));
        factor.confirm(VERIFY_AT.getEpochSecond() / 30 - 1, VERIFY_AT.minusSeconds(30));
        factors.saveAndFlush(factor);
      });

      List<FactorOperationStatusEnum> results = compete(() -> transaction(context).execute(
          status -> service.verifyActive(userId, "081804", VERIFY_AT)));

      assertThat(results).containsExactlyInAnyOrder(
          FactorOperationStatusEnum.USED, FactorOperationStatusEnum.REJECTED);
      TotpFactorEntity persisted = factors.findByUserIdOrderById(userId).getFirst();
      assertThat(persisted.getLastAcceptedStep()).isEqualTo(VERIFY_AT.getEpochSecond() / 30);
    });
  }

  private static TotpProtocolService protocol(AuthenticationKeyringService keyring) {
    SecondFactorConfig config = new SecondFactorConfig(6, 30, 1, 6, 10);
    RFWAuthenticationPropertiesConfig properties = mock(RFWAuthenticationPropertiesConfig.class);
    when(properties.secondFactor()).thenReturn(config);
    return new TotpProtocolService(new RFWTotpService(config), properties, keyring);
  }

  private static AuthenticationKeyringService keyring() {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) 9);
    return new AuthenticationKeyringService(new AuthenticationKeyringPropertiesConfig(
        true, "v1", Map.of("v1", Base64.getEncoder().encodeToString(key))));
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

  private static List<FactorOperationStatusEnum> compete(
      java.util.concurrent.Callable<FactorOperationStatusEnum> operation) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<FactorOperationStatusEnum> first = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      Future<FactorOperationStatusEnum> second = executor.submit(() -> {
        start.await(10, TimeUnit.SECONDS);
        return operation.call();
      });
      start.countDown();
      return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    } catch (Exception failure) {
      throw new IllegalStateException("Concurrent TOTP verification failed", failure);
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
