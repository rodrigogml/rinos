package br.com.rinos.app.backend.module.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.component.TenantStorageNamedLockComponent;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.service.TenantStorageProvisioningService;
import br.com.rinos.app.backend.module.storage.service.StorageOperationClaimService;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.config.StoragePropertiesConfig;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateStrategyResolverService;
import br.eng.rodrigogml.rfw.database.strategy.MySQLDatabaseUpdateStrategy;
import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Valida o registro global do storage contra um schema MySQL 9 descartável. */
@DisplayName("Persistência do armazenamento de tenant")
class TenantStoragePersistenceIT {

  private static final Instant NOW = Instant.parse("2026-08-29T20:00:00Z");

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
    new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"))
        .execute(dataSource);
  }

  @Test
  void registry_shouldPersistMappedEntitiesAndSafeAuditWithoutPhysicalConnectionDetails() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      TenantStorageRegistryRepository registries =
          context.getBean(TenantStorageRegistryRepository.class);
      StorageOperationRepository operations = context.getBean(StorageOperationRepository.class);
      StorageOperationStepRepository steps = context.getBean(StorageOperationStepRepository.class);
      StorageMigrationExecutionRepository migrations =
          context.getBean(StorageMigrationExecutionRepository.class);
      StorageStateTransitionRepository transitions =
          context.getBean(StorageStateTransitionRepository.class);
      StorageAuditEventRepository audits = context.getBean(StorageAuditEventRepository.class);
      long tenantId = insertTenant();

      transaction.executeWithoutResult(status -> {
        TenantStorageRegistryEntity registry = registries.saveAndFlush(new TenantStorageRegistryEntity(
            tenantId, identifier("11111111111111111111111111111111"), "20260829001"));
        StorageOperationEntity operation = operations.saveAndFlush(new StorageOperationEntity(
            UUID.randomUUID(), registry.getId(), StorageOperationType.PROVISION, UUID.randomUUID(),
            "storage-test"));
        steps.saveAndFlush(new StorageOperationStepEntity(operation.getId(),
            StorageOperationStepType.RESERVE));
        migrations.saveAndFlush(new StorageMigrationExecutionEntity(
            registry.getId(), operation.getId(), "20260829001", "20260829_001_update.sql",
            new byte[32], null, NOW));
        transitions.saveAndFlush(new StorageStateTransitionEntity(
            registry.getId(), operation.getId(), null, TenantStorageState.REQUESTED,
            StorageOperationStepType.RESERVE, StorageTransitionOriginType.SYSTEM, null,
            "tenant-storage-worker", "storage-test", "REQUESTED", NOW));
        audits.saveAndFlush(new StorageAuditEventEntity(
            "PROVISIONING_ACCEPTED", registry.getId(), operation.getId(), null,
            "tenant-storage-worker", "storage-test", "ACCEPTED", "{\"attempt\":1}", NOW));

        assertThat(registries.findByTenantId(tenantId))
            .map(TenantStorageRegistryEntity::getPhysicalIdentifier)
            .contains(identifier("11111111111111111111111111111111"));
        assertThat(operations.findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(
            registry.getId(), StorageOperationType.PROVISION, operation.getIdempotencyReference()))
            .isPresent();
        assertThat(steps.findByStorageOperationIdAndStepType(
            operation.getId(), StorageOperationStepType.RESERVE)).isPresent();
        assertThat(migrations.findByTenantStorageRegistryIdAndScriptVersion(
            registry.getId(), "20260829001")).isPresent();
      });

      JdbcTemplate jdbc = new JdbcTemplate(dataSource);
      assertThat(jdbc.queryForList("""
          SELECT column_name
          FROM information_schema.columns
          WHERE table_schema = DATABASE() AND table_name = 'storage_auditEvent'
          """, String.class))
          .doesNotContain("schemaName", "jdbcUrl", "password", "sql");
    });
  }

  @Test
  void registry_shouldEnforcePhysicalReservationAndOperationInvariants() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      TenantStorageRegistryRepository registries =
          context.getBean(TenantStorageRegistryRepository.class);
      StorageOperationRepository operations = context.getBean(StorageOperationRepository.class);
      long firstTenantId = insertTenant();
      long secondTenantId = insertTenant();

      TenantStorageRegistryEntity first = transaction.execute(status -> registries.saveAndFlush(
          new TenantStorageRegistryEntity(firstTenantId,
              identifier("22222222222222222222222222222222"), "20260829001")));

      assertThatThrownBy(() -> transaction.executeWithoutResult(status -> registries.saveAndFlush(
          new TenantStorageRegistryEntity(firstTenantId,
              identifier("33333333333333333333333333333333"), "20260829001"))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> transaction.executeWithoutResult(status -> registries.saveAndFlush(
          new TenantStorageRegistryEntity(secondTenantId,
              identifier("22222222222222222222222222222222"), "20260829001"))))
          .isInstanceOf(DataIntegrityViolationException.class);

      UUID idempotencyReference = UUID.randomUUID();
      transaction.executeWithoutResult(status -> operations.saveAndFlush(new StorageOperationEntity(
          UUID.randomUUID(), first.getId(), StorageOperationType.PROVISION, idempotencyReference,
          "storage-invariant")));
      assertThatThrownBy(() -> transaction.executeWithoutResult(status -> operations.saveAndFlush(
          new StorageOperationEntity(UUID.randomUUID(), first.getId(), StorageOperationType.PROVISION,
              idempotencyReference, "storage-invariant"))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> transaction.executeWithoutResult(status -> operations.saveAndFlush(
          new StorageOperationEntity(UUID.randomUUID(), first.getId(), StorageOperationType.MIGRATE,
              UUID.randomUUID(), "storage-invariant"))))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThatThrownBy(() -> new JdbcTemplate(dataSource).update("""
          INSERT INTO storage_stateTransition (
            idTenantStorageRegistry, resultingState, originType, systemOrigin,
            correlationId, safeResultCode, occurredAt
          ) VALUES (?, 'INVALID', 'SYSTEM', 'tenant-storage-worker',
            'storage-invariant', 'REJECTED', UTC_TIMESTAMP(6))
          """, first.getId()))
          .isInstanceOf(DataAccessException.class);
    });
  }

  @Test
  void provisioning_shouldCreateOnlyOneReservationAndOperation_whenSameProtocolArrivesConcurrently()
      throws InterruptedException {
    contextRunner().run(context -> {
      AccountBootstrapRequest request = insertAccountBootstrapContext();
      TenantStorageProvisioningService provisioning = context.getBean(TenantStorageProvisioningService.class);
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        Callable<AccountBootstrapResultStatus> call = () -> provisioning.requestProvisioning(request).status();
        List<AccountBootstrapResultStatus> results = executor.invokeAll(List.of(call, call), 15, TimeUnit.SECONDS)
            .stream()
            .map(future -> {
              try {
                return future.get();
              } catch (Exception exception) {
                throw new IllegalStateException(exception);
              }
            })
            .toList();

        assertThat(results).containsExactlyInAnyOrder(AccountBootstrapResultStatus.ACCEPTED,
            AccountBootstrapResultStatus.ACCEPTED);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_tenantRegistry", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_operation", Long.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM storage_operationStep", Long.class)).isOne();
      } finally {
        executor.shutdownNow();
      }
    });
  }

  @Test
  void claim_shouldPrioritizeMigrationAndResumeExpiredLease_whenQueueContainsBothCases() {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      TenantStorageRegistryRepository registries = context.getBean(TenantStorageRegistryRepository.class);
      StorageOperationRepository operations = context.getBean(StorageOperationRepository.class);
      StorageOperationClaimService claims = context.getBean(StorageOperationClaimService.class);
      StorageOperationEntity provisioning = transaction.execute(status -> createOperation(registries, operations,
          StorageOperationType.PROVISION));
      StorageOperationEntity migration = transaction.execute(status -> createOperation(registries, operations,
          StorageOperationType.MIGRATE));

      Optional<StorageOperationClaimVO> firstClaim = claims.claimNext("instance-a");

      assertThat(firstClaim).isPresent();
      assertThat(firstClaim.orElseThrow().operationPublicId()).isEqualTo(migration.getPublicId());
      transaction.executeWithoutResult(status -> {
        StorageOperationEntity claimedProvisioning = operations.findById(provisioning.getId()).orElseThrow();
        claimedProvisioning.claim("instance-a", Instant.now().minusSeconds(1));
        operations.saveAndFlush(claimedProvisioning);
      });

      Optional<StorageOperationClaimVO> resumedClaim = claims.claimNext("instance-b");

      assertThat(resumedClaim).isPresent();
      assertThat(resumedClaim.orElseThrow().operationPublicId()).isEqualTo(provisioning.getPublicId());
      assertThat(operations.findById(provisioning.getId()).orElseThrow().getLeaseOwner()).isEqualTo("instance-b");
      assertThat(operations.findById(provisioning.getId()).orElseThrow().getAttemptCount()).isEqualTo(2);
    });
  }

  @Test
  void claim_shouldAllowOnlyOneInstanceToAcquireSameQueuedOperation_whenInstancesRace()
      throws InterruptedException {
    contextRunner().run(context -> {
      TransactionTemplate transaction = transaction(context);
      TenantStorageRegistryRepository registries = context.getBean(TenantStorageRegistryRepository.class);
      StorageOperationRepository operations = context.getBean(StorageOperationRepository.class);
      StorageOperationClaimService claims = context.getBean(StorageOperationClaimService.class);
      transaction.executeWithoutResult(status -> createOperation(registries, operations, StorageOperationType.PROVISION));
      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        Callable<Optional<StorageOperationClaimVO>> first = () -> claims.claimNext("instance-a");
        Callable<Optional<StorageOperationClaimVO>> second = () -> claims.claimNext("instance-b");
        List<Future<Optional<StorageOperationClaimVO>>> results = executor.invokeAll(List.of(first, second), 15,
            TimeUnit.SECONDS);

        long acquired = results.stream().map(this::getFuture).filter(Optional::isPresent).count();

        assertThat(acquired).isOne();
      } finally {
        executor.shutdownNow();
      }
    });
  }

  @Test
  void namedLock_shouldSerializeSameTenantCatalogAcrossInstances() throws Exception {
    TenantStorageNamedLockComponent locks = new TenantStorageNamedLockComponent(
        new DatabaseUpdateStrategyResolverService(List.of(new MySQLDatabaseUpdateStrategy())));
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch firstEntered = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    CountDownLatch secondEntered = new CountDownLatch(1);
    AtomicInteger running = new AtomicInteger();
    AtomicInteger maximumRunning = new AtomicInteger();
    try {
      Future<?> first = executor.submit(() -> locks.executeExclusive(dataSource, Duration.ofSeconds(5), () -> {
        maximumRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
        firstEntered.countDown();
        await(releaseFirst);
        running.decrementAndGet();
      }));
      assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
      Future<?> second = executor.submit(() -> locks.executeExclusive(dataSource, Duration.ofSeconds(5), () -> {
        maximumRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
        secondEntered.countDown();
        running.decrementAndGet();
      }));

      assertThat(secondEntered.await(250, TimeUnit.MILLISECONDS)).isFalse();
      releaseFirst.countDown();
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);

      assertThat(maximumRunning.get()).isOne();
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
    }
  }

  private long insertTenant() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("""
        INSERT INTO account_tenant (publicId, status, version)
        VALUES (UUID_TO_BIN(UUID()), 'RESERVED', 0)
        """);
    return jdbc.queryForObject("SELECT MAX(idTenant) FROM account_tenant", Long.class);
  }

  private StorageOperationEntity createOperation(TenantStorageRegistryRepository registries,
      StorageOperationRepository operations, StorageOperationType type) {
    long tenantId = insertTenant();
    TenantStorageRegistryEntity registry = registries.saveAndFlush(new TenantStorageRegistryEntity(tenantId,
        identifier(UUID.randomUUID().toString().replace("-", "")), "20260829001"));
    return operations.saveAndFlush(new StorageOperationEntity(UUID.randomUUID(), registry.getId(), type,
        UUID.randomUUID(), "storage-operation-claim"));
  }

  private Optional<StorageOperationClaimVO> getFuture(Future<Optional<StorageOperationClaimVO>> future) {
    try {
      return future.get(5, TimeUnit.SECONDS);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for structural lock test");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for structural lock test", exception);
    }
  }

  private AccountBootstrapRequest insertAccountBootstrapContext() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID accountPublicId = UUID.randomUUID();
    UUID tenantPublicId = UUID.randomUUID();
    UUID protocolId = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO identity_user (email, normalizedEmail, status, globalActorRole, version)
        VALUES ('storage@example.test', 'storage@example.test', 'ACTIVE', 'USER', 0)
        """);
    long userId = jdbc.queryForObject("SELECT MAX(id) FROM identity_user", Long.class);
    jdbc.update("INSERT INTO account_tenant (publicId, status, version) VALUES (UUID_TO_BIN(?), 'RESERVED', 0)",
        tenantPublicId.toString());
    long tenantId = jdbc.queryForObject("SELECT MAX(idTenant) FROM account_tenant", Long.class);
    jdbc.update("""
        INSERT INTO account_account (publicId, idTenant, founderUserId, displayName, baseCurrency, timeZoneId, status, version)
        VALUES (UUID_TO_BIN(?), ?, ?, 'Storage test account', 'BRL', 'UTC', 'CREATING', 0)
        """, accountPublicId.toString(), tenantId, userId);
    long accountId = jdbc.queryForObject("SELECT MAX(idAccount) FROM account_account", Long.class);
    jdbc.update("""
        INSERT INTO account_creationIntent
          (publicId, protocolId, creatorUserId, idempotencyKey, payloadHash, idAccount, status, publicStage, version)
        VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, UUID_TO_BIN(?), UNHEX(REPEAT('00', 32)), ?,
          'ACCEPTED', 'ACCEPTED', 0)
        """, UUID.randomUUID().toString(), protocolId.toString(), userId, UUID.randomUUID().toString(), accountId);
    return new AccountBootstrapRequest(protocolId, accountPublicId, tenantPublicId, userId,
        "storage-concurrency-test");
  }

  private static TenantPhysicalIdentifier identifier(String value) {
    return new TenantPhysicalIdentifier(value);
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
        .withBean(DataSource.class, () -> dataSource)
        .withBean(StoragePropertiesConfig.class,
            () -> new StoragePropertiesConfig(Duration.ofSeconds(30), Duration.ofMinutes(10),
                Duration.ofSeconds(30), 3, 1))
        .withBean(TenantDatabaseCatalogService.class, TenantStoragePersistenceIT::catalogService);
  }

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  @EntityScan(basePackageClasses = {TenantStorageRegistryEntity.class, AccountEntity.class,
      AccountCreationIntentEntity.class, TenantEntity.class})
  @EnableJpaRepositories(basePackageClasses = {TenantStorageRegistryRepository.class, AccountRepository.class,
      AccountCreationIntentRepository.class, TenantRepository.class})
  @Import({TenantStorageProvisioningService.class, StorageOperationClaimService.class})
  static class RepositoryTestConfig {
  }

  private static TenantDatabaseCatalogService catalogService() {
    TenantDatabaseCatalogService catalog = mock(TenantDatabaseCatalogService.class);
    TenantDatabaseCatalogVO catalogView = mock(TenantDatabaseCatalogVO.class);
    when(catalogView.targetVersion()).thenReturn(new DatabaseVersionVO("20260829001"));
    when(catalog.inspect()).thenReturn(catalogView);
    return catalog;
  }
}
