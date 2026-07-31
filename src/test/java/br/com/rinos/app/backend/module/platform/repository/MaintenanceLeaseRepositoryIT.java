package br.com.rinos.app.backend.module.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import br.com.rinos.app.backend.module.platform.entity.MaintenanceLeaseEntity;
import br.com.rinos.app.backend.module.platform.service.MaintenanceExecutionService;
import br.com.rinos.app.backend.module.platform.service.MaintenanceLeaseService;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

/**
 * Valida as mutações condicionais do lease contra um MySQL 9 descartável.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@DisplayName("Persistência concorrente do lease de manutenção")
class MaintenanceLeaseRepositoryIT {

  private static final String LEASE_KEY = "global-maintenance";
  private static final long LEASE_TIMEOUT_MICROSECONDS = 300_000_000L;

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
   * Recria a tabela real antes de cada cenário.
   */
  @BeforeEach
  void resetDatabase() {
    Assumptions.assumeTrue(
        testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
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
   * Comprova que sessões distintas do mesmo {@code instanceId} não compartilham a liderança.
   */
  @Test
  void createIfAbsent_shouldChooseOneWinner_whenInstanceIdIsDuplicated() {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);

      CompetitionResult result = compete(
          repository,
          transaction,
          false,
          "duplicated-instance",
          "duplicated-instance");

      assertThat(result.firstWinner() ^ result.secondWinner()).isTrue();
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
   * Comprova que a líder antiga não renova nem volta a ser elegível depois de uma tomada.
   */
  @Test
  void oldLeader_shouldRemainFenced_whenReturningAfterTakeover() {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);
      String oldSessionId = UUID.randomUUID().toString();
      String newSessionId = UUID.randomUUID().toString();
      transaction.executeWithoutResult(status -> repository.createIfAbsent(
          LEASE_KEY,
          "old-instance",
          oldSessionId,
          -1));
      MaintenanceLeaseEntity oldLease = transaction.execute(
          status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow());
      transaction.executeWithoutResult(status -> repository.takeOverIfExpired(
          LEASE_KEY,
          "new-instance",
          newSessionId,
          LEASE_TIMEOUT_MICROSECONDS));

      Integer oldRenewal = transaction.execute(status -> repository.renewIfOwned(
          LEASE_KEY,
          "old-instance",
          oldSessionId,
          oldLease.getEpoch(),
          oldLease.getVersion(),
          LEASE_TIMEOUT_MICROSECONDS));
      Long oldProof = transaction.execute(status -> repository.countStableOwnership(
          LEASE_KEY,
          "old-instance",
          oldSessionId,
          oldLease.getEpoch(),
          0));
      MaintenanceLeaseEntity currentLease = transaction.execute(
          status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow());

      assertThat(oldRenewal).isZero();
      assertThat(oldProof).isZero();
      assertThat(currentLease.getEpoch()).isEqualTo(oldLease.getEpoch() + 1);
      assertThat(currentLease.getSessionId()).isEqualTo(newSessionId);
    });
  }

  /**
   * Comprova que a estabilização impede a nova líder de sobrepor um lote antigo em encerramento.
   *
   * @throws SQLException quando os marcos temporais não podem ser preparados
   */
  @Test
  void newLeader_shouldWaitForStabilization_whileOldBatchFinishes() throws SQLException {
    contextRunner().run(context -> {
      MaintenanceLeaseRepository repository =
          context.getBean(MaintenanceLeaseRepository.class);
      TransactionTemplate transaction = transaction(context);
      ExecutorService executor = Executors.newSingleThreadExecutor();
      CountDownLatch oldBatchStarted = new CountDownLatch(1);
      CountDownLatch releaseOldBatch = new CountDownLatch(1);
      AtomicInteger activeBatches = new AtomicInteger();
      AtomicInteger maximumActiveBatches = new AtomicInteger();
      String oldSessionId = UUID.randomUUID().toString();
      String newSessionId = UUID.randomUUID().toString();
      transaction.executeWithoutResult(status -> repository.createIfAbsent(
          LEASE_KEY,
          "old-instance",
          oldSessionId,
          LEASE_TIMEOUT_MICROSECONDS));
      moveAcquisitionToElevenMinutesAgo();
      long oldEpoch = transaction.execute(
          status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow().getEpoch());
      Long oldProofBeforeTakeover = transaction.execute(
          status -> repository.countStableOwnership(
              LEASE_KEY,
              "old-instance",
              oldSessionId,
              oldEpoch,
              600_000_000L));
      assertThat(oldProofBeforeTakeover).isEqualTo(1);

      Future<?> oldBatch = executor.submit(() -> transaction.executeWithoutResult(status -> {
        long proof = repository.countStableOwnership(
            LEASE_KEY,
            "old-instance",
            oldSessionId,
            oldEpoch,
            600_000_000L);
        if (proof != 1) {
          throw new IllegalStateException("A líder antiga não comprovou o lote inicial.");
        }
        enterBatch(activeBatches, maximumActiveBatches);
        oldBatchStarted.countDown();
        await(releaseOldBatch);
        activeBatches.decrementAndGet();
      }));

      try {
        await(oldBatchStarted);
        expireLease();
        transaction.executeWithoutResult(status -> repository.takeOverIfExpired(
            LEASE_KEY,
            "new-instance",
            newSessionId,
            LEASE_TIMEOUT_MICROSECONDS));
        long newEpoch = transaction.execute(
            status -> repository.findByLeaseKey(LEASE_KEY).orElseThrow().getEpoch());
        long newProofDuringDrain = transaction.execute(
            status -> repository.countStableOwnership(
                LEASE_KEY,
                "new-instance",
                newSessionId,
                newEpoch,
                600_000_000L));

        assertThat(activeBatches.get()).isEqualTo(1);
        assertThat(newProofDuringDrain).isZero();

        releaseOldBatch.countDown();
        oldBatch.get(10, TimeUnit.SECONDS);
        moveAcquisitionToElevenMinutesAgo();
        long newProofAfterDrain = transaction.execute(
            status -> repository.countStableOwnership(
                LEASE_KEY,
                "new-instance",
                newSessionId,
                newEpoch,
                600_000_000L));
        assertThat(newProofAfterDrain).isEqualTo(1);
        transaction.executeWithoutResult(status -> {
          long proof = repository.countStableOwnership(
              LEASE_KEY,
              "new-instance",
              newSessionId,
              newEpoch,
              600_000_000L);
          if (proof != 1) {
            throw new IllegalStateException("A nova líder perdeu a prova estabilizada.");
          }
          enterBatch(activeBatches, maximumActiveBatches);
          activeBatches.decrementAndGet();
        });

        assertThat(activeBatches.get()).isZero();
        assertThat(maximumActiveBatches.get()).isEqualTo(1);
      } catch (Exception exception) {
        throw new IllegalStateException("O cenário de drain não pôde ser concluído.", exception);
      } finally {
        releaseOldBatch.countDown();
        executor.shutdownNow();
      }
    });
  }

  /**
   * Comprova que o timeout configurado interrompe uma instrução antes de sua duração nominal.
   */
  @Test
  void executeBatch_shouldAbortDatabaseWork_whenTransactionTimesOut() {
    contextRunner().run(context -> {
      MaintenanceLeaseService leaseService = mock(MaintenanceLeaseService.class);
      MaintenanceLeaseVO lease = lease();
      when(leaseService.provesStableOwnership(lease)).thenReturn(true);
      MaintenanceExecutionService executionService = new MaintenanceExecutionService(
          leaseService,
          context.getBean(PlatformTransactionManager.class),
          shortTimeoutProperties());
      JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
      long startedAt = System.nanoTime();

      assertThatThrownBy(() -> executionService.executeBatch(
          lease,
          () -> jdbcTemplate.queryForObject("SELECT SLEEP(5)", Integer.class)))
          .isInstanceOf(RuntimeException.class);

      Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
      assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
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
   * Expira o lease no relógio do próprio MySQL.
   *
   * @throws SQLException quando o banco descartável não aceita a atualização
   */
  private void expireLease() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          UPDATE platform_maintenanceLease
          SET leaseUntil = TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))
          WHERE leaseKey = 'global-maintenance'
          """);
    }
  }

  /**
   * Entra em um lote e preserva a maior concorrência observada.
   *
   * @param activeBatches lotes atualmente ativos
   * @param maximumActiveBatches maior quantidade simultânea
   */
  private static void enterBatch(
      AtomicInteger activeBatches,
      AtomicInteger maximumActiveBatches) {
    int active = activeBatches.incrementAndGet();
    maximumActiveBatches.accumulateAndGet(active, Math::max);
  }

  /**
   * Aguarda uma barreira limitada e preserva interrupções da thread.
   *
   * @param latch barreira a aguardar
   */
  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("A barreira concorrente excedeu dez segundos.");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("A espera concorrente foi interrompida.", exception);
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
    return compete(
        repository,
        transaction,
        expiredLease,
        "instance-one",
        "instance-two");
  }

  /**
   * Executa a disputa permitindo controlar as identidades estáveis das duas sessões.
   *
   * @param repository repositório compartilhado e thread-safe
   * @param transaction executor de transações independentes por thread
   * @param expiredLease {@code true} quando a linha expirada já foi criada
   * @param firstInstanceId identidade estável da primeira sessão
   * @param secondInstanceId identidade estável da segunda sessão
   * @return resultado de propriedade observado por cada sessão
   */
  private CompetitionResult compete(
      MaintenanceLeaseRepository repository,
      TransactionTemplate transaction,
      boolean expiredLease,
      String firstInstanceId,
      String secondInstanceId) {
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
          firstInstanceId,
          firstSession,
          expiredLease);
      Future<Boolean> second = submitContender(
          executor,
          start,
          repository,
          transaction,
          secondInstanceId,
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

  /**
   * Cria o token usado no teste transacional sem depender de instantes locais para a decisão.
   *
   * @return token da sessão simulada
   */
  private static MaintenanceLeaseVO lease() {
    MaintenanceSessionVO owner = new MaintenanceSessionVO(
        "instance-one",
        UUID.fromString("65cb579b-1e02-4a89-83c6-f9a8af8f83ea"));
    return new MaintenanceLeaseVO(
        LEASE_KEY,
        owner,
        3,
        Instant.parse("2036-07-29T10:00:00Z"),
        Instant.parse("2036-07-29T10:30:00Z"),
        Instant.parse("2036-07-29T14:30:00Z"),
        5);
  }

  /**
   * Cria uma configuração curta, preservando timeout menor que estabilização.
   *
   * @return propriedades com timeout transacional de um segundo
   */
  private static MaintenancePropertiesConfig shortTimeoutProperties() {
    return new MaintenancePropertiesConfig(
        "instance-one",
        Duration.ofSeconds(1),
        Duration.ofMinutes(5),
        Duration.ofSeconds(2),
        Duration.ofSeconds(1));
  }

  private record CompetitionResult(boolean firstWinner, boolean secondWinner) {
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = MaintenanceLeaseEntity.class)
  @EnableJpaRepositories(basePackageClasses = MaintenanceLeaseRepository.class)
  static class RepositoryTestConfig {
  }
}
