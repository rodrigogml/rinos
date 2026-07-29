package br.com.rinos.app.backend.module.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import br.com.rinos.app.backend.module.platform.entity.MaintenanceLeaseEntity;

/**
 * Valida as mutações condicionais do lease contra um MySQL 9 descartável.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Persistência concorrente do lease de manutenção")
class MaintenanceLeaseRepositoryIT {

  private static final String LEASE_KEY = "global-maintenance";
  private static final long LEASE_TIMEOUT_MICROSECONDS = 300_000_000L;

  @Container
  private static final MySQLContainer MYSQL = new MySQLContainer("mysql:9.0")
      .withDatabaseName("rinos_global")
      .withUsername("rinos")
      .withPassword("rinos-test");

  private DataSource dataSource;

  /**
   * Recria a tabela real antes de cada cenário.
   *
   * @throws SQLException quando o schema descartável não pode ser limpo
   */
  @BeforeEach
  void resetDatabase() throws SQLException {
    dataSource = new DriverManagerDataSource(
        MYSQL.getJdbcUrl(),
        MYSQL.getUsername(),
        MYSQL.getPassword());
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS platform_maintenanceLease");
    }
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"));
    populator.execute(dataSource);
  }

  /**
   * Comprova que duas sessões concorrentes produzem somente um proprietário inicial.
   */
  @Test
  void createIfAbsent_shouldChooseOneWinner_whenTwoSessionsCompete() {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);

      CompetitionResult result = compete(repository, transaction, false);

      assertThat(result.firstWinner() ^ result.secondWinner()).isTrue();
      MaintenanceLeaseEntity persisted = transaction.execute(
          status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow());
      assertThat(persisted.getEpoch()).isEqualTo(1);
      assertThat(persisted.getVersion()).isZero();
    });
  }

  /**
   * Comprova que duas tomadas concorrentes incrementam o epoch uma única vez.
   */
  @Test
  void takeOverIfExpired_shouldChooseOneWinner_whenTwoSessionsCompete() {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);
      transaction.executeWithoutResult(status -> repository.createIfAbsent(
          LEASE_KEY,
          "expired-instance",
          UUID.randomUUID().toString(),
          -1));

      CompetitionResult result = compete(repository, transaction, true);

      assertThat(result.firstWinner() ^ result.secondWinner()).isTrue();
      MaintenanceLeaseEntity persisted = transaction.execute(
          status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow());
      assertThat(persisted.getEpoch()).isEqualTo(2);
      assertThat(persisted.getVersion()).isEqualTo(1);
    });
  }

  /**
   * Comprova que versão, fencing e proprietário condicionam cada renovação.
   */
  @Test
  void renewIfOwned_shouldRejectUpdate_whenAnyOwnershipTokenIsStale() {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);
      String sessionId = UUID.randomUUID().toString();
      transaction.executeWithoutResult(status -> repository.createIfAbsent(
          LEASE_KEY,
          "instance-one",
          sessionId,
          LEASE_TIMEOUT_MICROSECONDS));
      MaintenanceLeaseEntity initial = transaction.execute(
          status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow());

      Integer renewed = transaction.execute(status -> repository.renewIfOwned(
          LEASE_KEY,
          "instance-one",
          sessionId,
          initial.getEpoch(),
          initial.getVersion(),
          LEASE_TIMEOUT_MICROSECONDS));
      Integer staleVersion = transaction.execute(status -> repository.renewIfOwned(
          LEASE_KEY,
          "instance-one",
          sessionId,
          initial.getEpoch(),
          initial.getVersion(),
          LEASE_TIMEOUT_MICROSECONDS));
      Integer wrongSession = transaction.execute(status -> repository.renewIfOwned(
          LEASE_KEY,
          "instance-one",
          UUID.randomUUID().toString(),
          initial.getEpoch(),
          initial.getVersion() + 1,
          LEASE_TIMEOUT_MICROSECONDS));

      assertThat(renewed).isEqualTo(1);
      assertThat(staleVersion).isZero();
      assertThat(wrongSession).isZero();
    });
  }

  /**
   * Comprova que a espera de estabilização é calculada no MySQL e bloqueia a aquisição recente.
   *
   * @throws SQLException quando o instante de aquisição não pode ser preparado
   */
  @Test
  void countStableOwnership_shouldReturnOne_onlyAfterDatabaseStabilization() throws SQLException {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);
      String sessionId = UUID.randomUUID().toString();
      transaction.executeWithoutResult(status -> repository.createIfAbsent(
          LEASE_KEY,
          "instance-one",
          sessionId,
          LEASE_TIMEOUT_MICROSECONDS));

      Long recentCount = transaction.execute(status -> repository.countStableOwnership(
          LEASE_KEY,
          "instance-one",
          sessionId,
          1,
          600_000_000L));
      moveAcquisitionToElevenMinutesAgo();
      Long stableCount = transaction.execute(status -> repository.countStableOwnership(
          LEASE_KEY,
          "instance-one",
          sessionId,
          1,
          600_000_000L));

      assertThat(recentCount).isZero();
      assertThat(stableCount).isEqualTo(1);
    });
  }

  /**
   * Move somente o marco de aquisição para simular a espera sem depender do relógio do teste.
   *
   * @throws SQLException quando a atualização do banco descartável falha
   */
  private void moveAcquisitionToElevenMinutesAgo() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          UPDATE platform_maintenanceLease
          SET acquiredAt = TIMESTAMPADD(MINUTE, -11, UTC_TIMESTAMP(6))
          WHERE leaseKey = 'global-maintenance'
          """);
    }
  }

  /**
   * Executa duas disputas em transações e conexões independentes.
   *
   * @param repository repositório compartilhado e thread-safe
   * @param transaction executor de transações independentes por thread
   * @param expiredLease {@code true} quando a linha expirada já foi criada
   * @return resultado de propriedade observado por cada sessão
   */
  private CompetitionResult compete(
      MaintenanceLeaseRepository repository,
      TransactionTemplate transaction,
      boolean expiredLease) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    String firstSession = UUID.randomUUID().toString();
    String secondSession = UUID.randomUUID().toString();
    try {
      Future<Boolean> first = submitContender(
          executor,
          start,
          repository,
          transaction,
          "instance-one",
          firstSession,
          expiredLease);
      Future<Boolean> second = submitContender(
          executor,
          start,
          repository,
          transaction,
          "instance-two",
          secondSession,
          expiredLease);
      start.countDown();
      return new CompetitionResult(
          first.get(10, TimeUnit.SECONDS),
          second.get(10, TimeUnit.SECONDS));
    } catch (Exception exception) {
      throw new IllegalStateException("A disputa concorrente não pôde ser concluída.", exception);
    } finally {
      executor.shutdownNow();
    }
  }

  /**
   * Agenda um candidato e confirma a propriedade somente após reler a linha persistida.
   *
   * @param executor pool concorrente
   * @param start barreira comum de início
   * @param repository acesso persistente
   * @param transaction executor transacional
   * @param instanceId identidade da instância candidata
   * @param sessionId identidade da sessão candidata
   * @param expiredLease {@code true} para disputar somente a tomada
   * @return resultado futuro de propriedade
   */
  private Future<Boolean> submitContender(
      ExecutorService executor,
      CountDownLatch start,
      MaintenanceLeaseRepository repository,
      TransactionTemplate transaction,
      String instanceId,
      String sessionId,
      boolean expiredLease) {
    return executor.submit(() -> {
      start.await(10, TimeUnit.SECONDS);
      Boolean winner = transaction.execute(status -> {
        if (!expiredLease) {
          repository.createIfAbsent(
              LEASE_KEY,
              instanceId,
              sessionId,
              LEASE_TIMEOUT_MICROSECONDS);
        }
        repository.takeOverIfExpired(
            LEASE_KEY,
            instanceId,
            sessionId,
            LEASE_TIMEOUT_MICROSECONDS);
        Optional<MaintenanceLeaseEntity> lease = repository.findByLeaseKey(LEASE_KEY);
        return lease
            .filter(entity -> entity.getInstanceId().equals(instanceId))
            .filter(entity -> entity.getSessionId().equals(sessionId))
            .isPresent();
      });
      return Boolean.TRUE.equals(winner);
    });
  }

  /**
   * Cria o contexto mínimo de JPA sobre o banco descartável.
   *
   * @return executor de contexto isolado
   */
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

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  private record CompetitionResult(boolean firstWinner, boolean secondWinner) {
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = MaintenanceLeaseEntity.class)
  @EnableJpaRepositories(basePackageClasses = MaintenanceLeaseRepository.class)
  static class RepositoryTestConfig {
  }
}
