package br.com.rinos.app.backend.module.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.List;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.rinos.app.backend.module.identity.entity.PasskeyCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityReferenceService;
import br.com.rinos.app.backend.module.identity.service.RecoveryCodeService;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

/** Valida unicidade e consumo concorrente dos fatores contra MySQL 9. */
@DisplayName("Persistência dos fatores de autenticação")
class AuthenticationFactorRepositoryIT {
  private static final Instant NOW = Instant.parse("2026-08-08T15:00:00Z");
  private static MySqlTestDatabase testDatabase;
  private DataSource dataSource;

  @BeforeAll static void startDatabase() { testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null); }
  @AfterAll static void stopDatabase() { if (testDatabase != null) testDatabase.close(); }
  @BeforeEach void resetDatabase() {
    Assumptions.assumeTrue(testDatabase != null, "MySQL de teste indisponível");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql")).execute(dataSource);
  }

  @Test
  void recoveryCode_shouldHaveOneConcurrentConsumer() {
    contextRunner().run(context -> {
      UserRepository users = context.getBean(UserRepository.class);
      RecoveryCodeSetRepository sets = context.getBean(RecoveryCodeSetRepository.class);
      RecoveryCodeRepository codes = context.getBean(RecoveryCodeRepository.class);
      RecoveryCodeService service = new RecoveryCodeService(users, sets, codes, new IdentityReferenceService());
      Long userId = transaction(context).execute(status -> users.saveAndFlush(new UserEntity(
          "recovery@example.test", "recovery@example.test", UserStatusEnum.ACTIVE)).getId());
      List<String> hashes = java.util.stream.IntStream.rangeClosed(1, 10)
          .mapToObj(index -> "hash-" + index).toList();
      transaction(context).executeWithoutResult(status -> service.replace(userId, hashes, NOW));

      List<FactorOperationStatusEnum> results = compete(() -> transaction(context).execute(status ->
          service.consume(userId, "hash-1"::equals, NOW.plusSeconds(1))));

      assertThat(results).containsExactlyInAnyOrder(
          FactorOperationStatusEnum.USED, FactorOperationStatusEnum.REJECTED);
      transaction(context).executeWithoutResult(status -> {
        var active = sets.findByUserIdAndStatus(userId, RecoveryCodeSetStatusEnum.ACTIVE).orElseThrow();
        assertThat(codes.countByCodeSetIdAndStatus(active.getId(), RecoveryCodeStatusEnum.AVAILABLE)).isEqualTo(9);
      });
    });
  }

  @Test
  void passkeyCredential_shouldRejectDuplicateCredentialId() {
    contextRunner().run(context -> {
      UserRepository users = context.getBean(UserRepository.class);
      PasskeyUserRepository owners = context.getBean(PasskeyUserRepository.class);
      PasskeyCredentialRepository credentials = context.getBean(PasskeyCredentialRepository.class);
      PasskeyUserEntity owner = transaction(context).execute(status -> {
        UserEntity user = users.saveAndFlush(new UserEntity(
            "passkey@example.test", "passkey@example.test", UserStatusEnum.ACTIVE));
        return owners.saveAndFlush(new PasskeyUserEntity(user, new byte[32]));
      });
      byte[] credentialId = new byte[] {1, 2, 3};
      transaction(context).executeWithoutResult(status -> credentials.saveAndFlush(
          credential(owner, credentialId)));

      assertThatThrownBy(() -> transaction(context).executeWithoutResult(status ->
          credentials.saveAndFlush(credential(owner, credentialId))))
          .isInstanceOf(DataIntegrityViolationException.class);
    });
  }

  private static PasskeyCredentialEntity credential(PasskeyUserEntity owner, byte[] id) {
    return new PasskeyCredentialEntity(owner, UUID.randomUUID(), "public-key", id,
        new byte[] {4}, 0, true, false, false, "internal", new byte[] {5},
        new byte[] {6}, "Chave");
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class))
        .withUserConfiguration(RepositoryTestConfig.class)
        .withPropertyValues("spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
        .withBean(DataSource.class, () -> dataSource);
  }
  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }
  private static List<FactorOperationStatusEnum> compete(
      java.util.concurrent.Callable<FactorOperationStatusEnum> operation) {
    ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
    try {
      Future<FactorOperationStatusEnum> a = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return operation.call(); });
      Future<FactorOperationStatusEnum> b = executor.submit(() -> { start.await(10, TimeUnit.SECONDS); return operation.call(); });
      start.countDown(); return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
    } catch (Exception exception) { throw new IllegalStateException(exception); }
    finally { executor.shutdownNow(); }
  }
  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = UserEntity.class)
  @EnableJpaRepositories(basePackageClasses = UserRepository.class)
  static class RepositoryTestConfig { }
}
