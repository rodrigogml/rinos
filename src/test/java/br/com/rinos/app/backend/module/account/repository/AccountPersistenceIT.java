package br.com.rinos.app.backend.module.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.enums.AccountCreationResultStatus;
import br.com.rinos.app.api.module.account.vo.AccountCreationResult;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.service.AccountCreationAcceptanceService;
import br.com.rinos.app.backend.module.account.service.AccountCreationAdmissionService;
import br.com.rinos.app.backend.module.account.service.AccountCreationStatusService;
import br.com.rinos.app.backend.module.identity.entity.OriginWindowEntity;
import br.com.rinos.app.backend.module.identity.repository.OriginWindowRepository;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;
import br.com.rinos.app.config.OriginPropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

/** Gate MySQL do aceite atômico e idempotente de uma conta. */
class AccountPersistenceIT {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
  private static final OriginAddressVO ORIGIN = new OriginAddressService().normalize("203.0.113.10");
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
  void resetDatabase() throws SQLException {
    Assumptions.assumeTrue(testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ClassPathResource("db/global/init/01-ddl.sql"))
        .execute(dataSource);
    execute("""
        INSERT INTO identity_user (email, normalizedEmail, status)
        VALUES ('founder@example.com', 'founder@example.com', 'ACTIVE'),
               ('other@example.com', 'other@example.com', 'ACTIVE')
        """);
  }

  @Test
  void accept_shouldPersistOneAggregateAndReplayTheSamePayload() {
    contextRunner().run(context -> {
      AccountCreationAcceptanceService service =
          context.getBean(AccountCreationAcceptanceService.class);
      AccountCreationRequest request = request(UUID.randomUUID(), "Conta A");

      var accepted = service.accept(1L, request, "accept", NOW, ORIGIN, true);
      var replayed = service.accept(1L, request, "replay", NOW.plusSeconds(1), ORIGIN, true);

      assertThat(accepted.status()).isEqualTo(AccountCreationResultStatus.ACCEPTED);
      assertThat(replayed.status()).isEqualTo(AccountCreationResultStatus.REPLAYED);
      assertThat(replayed.protocolId()).isEqualTo(accepted.protocolId());
      assertThat(replayed.accountPublicId()).isEqualTo(accepted.accountPublicId());
      assertCoreRows(1);
    });
  }

  @Test
  void accept_shouldRejectReuseOfKeyWithDifferentPayload() {
    contextRunner().run(context -> {
      AccountCreationAcceptanceService service =
          context.getBean(AccountCreationAcceptanceService.class);
      UUID key = UUID.randomUUID();

      service.accept(1L, request(key, "Conta A"), "first", NOW, ORIGIN, true);
      var conflict = service.accept(1L, request(key, "Conta B"), "second", NOW, ORIGIN, true);

      assertThat(conflict.status()).isEqualTo(AccountCreationResultStatus.CONFLICT);
      assertThat(conflict.safeReasonCode()).isEqualTo("ACCOUNT_IDEMPOTENCY_CONFLICT");
      assertCoreRows(1);
    });
  }

  @Test
  void accept_shouldRollbackEverythingWhenLateAuditConstraintFails() {
    contextRunner().run(context -> {
      AccountCreationAcceptanceService service =
          context.getBean(AccountCreationAcceptanceService.class);

      assertThatThrownBy(() -> service.accept(
          1L, request(UUID.randomUUID(), "Conta"), "x".repeat(101), NOW, ORIGIN, true))
          .isInstanceOf(DataAccessException.class);

      assertCoreRows(0);
    });
  }

  @Test
  void accept_shouldConvergeConcurrentInstancesOnOneIntent() {
    contextRunner().run(context -> {
      AccountCreationAcceptanceService service =
          context.getBean(AccountCreationAcceptanceService.class);
      AccountCreationRequest request = request(UUID.randomUUID(), "Conta concorrente");
      var executor = Executors.newFixedThreadPool(2);
      try {
        Callable<AccountCreationResultStatus> call =
            () -> service.accept(1L, request, UUID.randomUUID().toString(), NOW, ORIGIN, true).status();
        List<AccountCreationResultStatus> results = executor.invokeAll(
            List.of(call, call), 15, TimeUnit.SECONDS).stream().map(future -> {
              try {
                return future.get();
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            }).toList();

        assertThat(results).containsExactlyInAnyOrder(
            AccountCreationResultStatus.ACCEPTED, AccountCreationResultStatus.REPLAYED);
        assertCoreRows(1);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(exception);
      } finally {
        executor.shutdownNow();
      }
    });
  }

  @Test
  void status_shouldOnlyBeVisibleToTheCreator() {
    contextRunner().run(context -> {
      var acceptance = context.getBean(AccountCreationAcceptanceService.class);
      var status = context.getBean(AccountCreationStatusService.class);
      var accepted = acceptance.accept(
          1L, request(UUID.randomUUID(), "Conta"), "status", NOW, ORIGIN, true);

      assertThat(status.find(1L, accepted.protocolId()).accountPublicId())
          .isEqualTo(accepted.accountPublicId());
      assertThatThrownBy(() -> status.find(2L, accepted.protocolId()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("account creation status is unavailable");
    });
  }

  @Test
  void accept_shouldKeepOriginBlockedAfterItsCountingWindowExpires() {
    contextRunner().run(context -> {
      AccountCreationAcceptanceService service =
          context.getBean(AccountCreationAcceptanceService.class);
      for (int index = 0; index < 5; index++) {
        AccountCreationResultStatus status = service.accept(
            1L,
            request(UUID.randomUUID(), "Conta " + index),
            "limit-" + index,
            Instant.now(),
            ORIGIN,
            true).status();
        assertThat(status).isEqualTo(AccountCreationResultStatus.ACCEPTED);
      }
      assertThat(service.accept(
          1L,
          request(UUID.randomUUID(), "Bloqueada"),
          "limit-block",
          Instant.now(),
          ORIGIN,
          true).safeReasonCode()).isEqualTo("ACCOUNT_RATE_LIMITED");
      try {
        execute("""
            UPDATE security_originWindow
            SET windowEndsAt = TIMESTAMPADD(MINUTE, -1, CURRENT_TIMESTAMP(6))
            WHERE operation = 'ACCOUNT_CREATION'
            """);
      } catch (SQLException exception) {
        throw new IllegalStateException(exception);
      }

      AccountCreationResult result = service.accept(
          1L,
          request(UUID.randomUUID(), "Ainda bloqueada"),
          "limit-after-window",
          Instant.now(),
          ORIGIN,
          true);

      assertThat(result.status()).isEqualTo(AccountCreationResultStatus.REJECTED);
      assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_RATE_LIMITED");
      assertCoreRows(5);
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
            "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.hibernate.naming.physical-strategy="
                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "spring.jpa.properties.hibernate.jdbc.time_zone=UTC")
        .withBean(DataSource.class, () -> dataSource);
  }

  private static AccountCreationRequest request(UUID key, String displayName) {
    return new AccountCreationRequest(
        key, displayName, "BRL", "America/Sao_Paulo", null, true);
  }

  private void assertCoreRows(long expected) {
    assertThat(count("account_tenant")).isEqualTo(expected);
    assertThat(count("account_account")).isEqualTo(expected);
    assertThat(count("account_creationIntent")).isEqualTo(expected);
    assertThat(count("account_provisioningCheckpoint")).isEqualTo(expected * 4);
    assertThat(count("account_outboxEvent")).isEqualTo(expected);
    assertThat(count("account_auditEvent")).isEqualTo(expected);
  }

  private void execute(String sql) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private long count(String table) {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    } catch (SQLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EntityScan(basePackageClasses = {AccountEntity.class, OriginWindowEntity.class})
  @EnableJpaRepositories(basePackageClasses = {AccountRepository.class, OriginWindowRepository.class})
  @Import({
      OriginLimitService.class,
      AccountCreationAdmissionService.class,
      AccountCreationAcceptanceService.class,
      AccountCreationStatusService.class})
  static class RepositoryTestConfig {

    @org.springframework.context.annotation.Bean
    OriginPropertiesConfig originPropertiesConfig() {
      return new OriginPropertiesConfig(0, 20, java.time.Duration.ofHours(24), java.time.Duration.ofDays(30));
    }

    @org.springframework.context.annotation.Bean
    AccountCreationPropertiesConfig accountCreationPropertiesConfig() {
      return new AccountCreationPropertiesConfig(
          0,
          java.time.Duration.ofHours(1),
          5,
          java.time.Duration.ofMinutes(15),
          java.time.Duration.ofDays(30),
          25,
          java.time.Duration.ofMinutes(2),
          java.time.Duration.ofMinutes(1),
          java.time.Duration.ofHours(1));
    }
  }
}
