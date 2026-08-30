package br.com.rinos.app.backend.module.storage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.backend.module.storage.entity.StorageAuditEventEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageStateTransitionEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.StorageTransitionOriginType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

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
        new ClassPathResource("db/global/init/01-ddl.sql"),
        new ClassPathResource("db/global/update/20260829_001_update.sql"))
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

  private long insertTenant() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("""
        INSERT INTO account_tenant (publicId, status, version)
        VALUES (UUID_TO_BIN(UUID()), 'RESERVED', 0)
        """);
    return jdbc.queryForObject("SELECT MAX(idTenant) FROM account_tenant", Long.class);
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
        .withBean(DataSource.class, () -> dataSource);
  }

  private static TransactionTemplate transaction(ApplicationContext context) {
    return new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
  }

  @Configuration(proxyBeanMethods = false)
  @EntityScan(basePackageClasses = TenantStorageRegistryEntity.class)
  @EnableJpaRepositories(basePackageClasses = TenantStorageRegistryRepository.class)
  static class RepositoryTestConfig {
  }
}
