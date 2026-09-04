package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.component.TenantDataSourceFactory;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseStructureVerifier;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseUpdateRequestFactory;
import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageMigrationExecutionState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.enums.TenantStorageState;
import br.com.rinos.app.backend.module.storage.repository.StorageAuditEventRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageMigrationExecutionRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageStateTransitionRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateExecutorService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateOrchestratorService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdatePlanService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptDiscoveryService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptNameService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateScriptValidationService;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateStrategyResolverService;
import br.eng.rodrigogml.rfw.database.service.DatabaseVersionService;
import br.eng.rodrigogml.rfw.database.service.SqlScriptParserService;
import br.eng.rodrigogml.rfw.database.strategy.MySQLDatabaseUpdateStrategy;
import br.eng.rodrigogml.rfw.logging.RFWLogger;

/**
 * Confirma a execução real da RFW sobre um schema de tenant controlado e isolado no MySQL 9.
 *
 * <p>A classe usa somente o schema físico literal reservado aos testes de storage e nunca conecta ao
 * {@code rinos_global}. O catálogo global que recebe mocks representa exclusivamente as confirmações duráveis;
 * criação do schema, lock RFW, DDL, versão e verificação estrutural são reais.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@DisplayName("Migration física isolada de tenant")
class TenantStorageMigrationOperationExecutorIT {

  private static final Instant NOW = Instant.parse("2026-08-30T19:00:00Z");
  private static final String VERSION = "20260829001";
  private static final TenantPhysicalIdentifier IDENTIFIER =
      new TenantPhysicalIdentifier("0f7c22fb0eaa4ea8ad9a5e1f0b730001");

  private static MySqlTestDatabase testDatabase;

  private HikariDataSource globalCatalogDataSource;

  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      dropSchema();
      testDatabase.close();
    }
  }

  @BeforeEach
  void createOutdatedTenantSchema() throws Exception {
    Assumptions.assumeTrue(testDatabase != null, "MySQL de teste indisponível");
    dropSchema();
    try (Connection connection = testDatabase.createServerDataSource().getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE `" + IDENTIFIER.schemaName()
          + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
      statement.execute("CREATE OR REPLACE SQL SECURITY INVOKER VIEW `" + IDENTIFIER.schemaName()
          + "`.databaseVersion AS SELECT '00000000000' AS version");
    }
    globalCatalogDataSource = testDatabase.createLazyGlobalCatalogDataSource();
  }

  @AfterEach
  void cleanupTenantSchema() {
    if (globalCatalogDataSource != null) {
      globalCatalogDataSource.close();
    }
    if (testDatabase != null) {
      dropSchema();
    }
  }

  @Test
  void execute_shouldApplyRfwCatalogOnceAndConfirmEvidence_whenTenantIsOutdated() throws Exception {
    Fixture fixture = new Fixture(globalCatalogDataSource);

    fixture.executor().execute(fixture.claim());
    fixture.executor().execute(fixture.claim());

    assertThat(fixture.operation().getOperationState()).isEqualTo(StorageOperationState.COMPLETED);
    assertThat(fixture.registry().getStorageState()).isEqualTo(TenantStorageState.READY);
    assertThat(fixture.migrations()).singleElement().satisfies(execution ->
        assertThat(execution.getExecutionState()).isEqualTo(StorageMigrationExecutionState.COMPLETED));
    assertThat(readVersion()).isEqualTo(VERSION);
    assertThat(tableExists("core_tenantBootstrap")).isTrue();
  }

  private static void dropSchema() {
    try (Connection connection = testDatabase.createServerDataSource().getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS `" + IDENTIFIER.schemaName() + "`");
    } catch (Exception exception) {
      throw new IllegalStateException("Não foi possível limpar o schema físico de migration de teste.", exception);
    }
  }

  private String readVersion() throws Exception {
    try (HikariDataSource tenantDataSource = new TenantDataSourceFactory(globalCatalogDataSource).create(IDENTIFIER);
        Connection connection = tenantDataSource.getConnection(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT version FROM databaseVersion")) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private boolean tableExists(String tableName) throws Exception {
    try (HikariDataSource tenantDataSource = new TenantDataSourceFactory(globalCatalogDataSource).create(IDENTIFIER);
        Connection connection = tenantDataSource.getConnection();
        ResultSet result = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, null)) {
      return result.next();
    }
  }

  private static final class Fixture {
    private final StorageOperationRepository operations = mock(StorageOperationRepository.class);
    private final StorageOperationStepRepository steps = mock(StorageOperationStepRepository.class);
    private final TenantStorageRegistryRepository registries = mock(TenantStorageRegistryRepository.class);
    private final StorageMigrationExecutionRepository migrationRepository = mock(StorageMigrationExecutionRepository.class);
    private final StorageStateTransitionRepository transitions = mock(StorageStateTransitionRepository.class);
    private final StorageAuditEventRepository audits = mock(StorageAuditEventRepository.class);
    private final StorageOperationEntity operation = TenantStorageMigrationOperationExecutorIT.operation();
    private final TenantStorageRegistryEntity registry = TenantStorageMigrationOperationExecutorIT.registry();
    private final StorageOperationStepEntity step = new StorageOperationStepEntity(operation.getId(),
        StorageOperationStepType.MIGRATE);
    private final List<StorageMigrationExecutionEntity> migrations = new ArrayList<>();
    private final TenantDataSourceFactory dataSourceFactory;
    private final TenantDatabaseCatalogService catalogService;

    private Fixture(HikariDataSource globalCatalogDataSource) {
      dataSourceFactory = new TenantDataSourceFactory(globalCatalogDataSource);
      catalogService = catalogService();
      when(operations.findByPublicIdForUpdate(operation.getPublicId())).thenReturn(Optional.of(operation));
      when(operations.saveAndFlush(any(StorageOperationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
      when(registries.findById(registry.getId())).thenReturn(Optional.of(registry));
      when(registries.saveAndFlush(any(TenantStorageRegistryEntity.class))).thenAnswer(invocation ->
          invocation.getArgument(0));
      when(steps.findByStorageOperationIdAndStepType(operation.getId(), StorageOperationStepType.MIGRATE))
          .thenReturn(Optional.of(step));
      when(steps.saveAndFlush(any(StorageOperationStepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
      when(migrationRepository.findByTenantStorageRegistryIdAndScriptVersion(registry.getId(), VERSION))
          .thenAnswer(invocation -> migrations.stream().findFirst());
      when(migrationRepository.saveAndFlush(any(StorageMigrationExecutionEntity.class))).thenAnswer(invocation -> {
        StorageMigrationExecutionEntity execution = invocation.getArgument(0);
        if (!migrations.contains(execution)) {
          migrations.add(execution);
        }
        return execution;
      });
      when(migrationRepository.findAllByTenantStorageRegistryIdOrderByScriptVersion(registry.getId()))
          .thenAnswer(invocation -> List.copyOf(migrations));
      when(migrationRepository.findAllByStorageOperationIdOrderByScriptVersion(operation.getId()))
          .thenAnswer(invocation -> List.copyOf(migrations));
    }

    private TenantStorageMigrationOperationExecutor executor() {
      DatabaseUpdatePropertiesConfig properties = new DatabaseUpdatePropertiesConfig();
      properties.setLockTimeout(Duration.ofSeconds(30));
      DatabaseVersionService versionService = new DatabaseVersionService();
      TenantDatabaseStructureVerifier verifier = new TenantDatabaseStructureVerifier(versionService, catalogService);
      PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
      when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
      return new TenantStorageMigrationOperationExecutor(operations, steps, registries, migrationRepository,
          transitions, audits, new StorageOperationStateTransitionService(),
          new StorageOperationStepStateTransitionService(), new TenantStorageStateTransitionService(),
          dataSourceFactory, new TenantDatabaseUpdateRequestFactory(properties), catalogService, verifier,
          versionService, updateOrchestrator(properties, versionService), transactionManager,
          Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private StorageOperationClaimVO claim() {
      return new StorageOperationClaimVO(operation.getPublicId(), registry.getId(), StorageOperationType.MIGRATE,
          "instance-a", NOW.plusSeconds(600));
    }

    private StorageOperationEntity operation() { return operation; }
    private TenantStorageRegistryEntity registry() { return registry; }
    private List<StorageMigrationExecutionEntity> migrations() { return migrations; }
  }

  private static TenantStorageRegistryEntity registry() {
    TenantStorageRegistryEntity registry = new TenantStorageRegistryEntity(12L, IDENTIFIER, VERSION);
    ReflectionTestUtils.setField(registry, "id", 7L);
    registry.changeState(TenantStorageState.MIGRATING);
    return registry;
  }

  private static StorageOperationEntity operation() {
    StorageOperationEntity operation = new StorageOperationEntity(UUID.randomUUID(), 7L,
        StorageOperationType.MIGRATE, UUID.randomUUID(), "migration-physical-test");
    ReflectionTestUtils.setField(operation, "id", 9L);
    operation.claim("instance-a", NOW.plusSeconds(600));
    return operation;
  }

  private static TenantDatabaseCatalogService catalogService() {
    SqlScriptParserService parser = new SqlScriptParserService();
    DatabaseUpdateScriptValidationService validation = new DatabaseUpdateScriptValidationService(parser);
    return new TenantDatabaseCatalogService(new DatabaseUpdateScriptDiscoveryService(
        new PathMatchingResourcePatternResolver(), new DatabaseUpdateScriptNameService()), validation);
  }

  private static DatabaseUpdateOrchestratorService updateOrchestrator(DatabaseUpdatePropertiesConfig properties,
      DatabaseVersionService versionService) {
    SqlScriptParserService parser = new SqlScriptParserService();
    DatabaseUpdateScriptValidationService validation = new DatabaseUpdateScriptValidationService(parser);
    DatabaseUpdateScriptDiscoveryService discovery = new DatabaseUpdateScriptDiscoveryService(
        new PathMatchingResourcePatternResolver(), new DatabaseUpdateScriptNameService());
    return new DatabaseUpdateOrchestratorService(mock(ObjectProvider.class), properties, discovery, validation,
        versionService, new DatabaseUpdatePlanService(), new DatabaseUpdateStrategyResolverService(
            List.of(new MySQLDatabaseUpdateStrategy())), new DatabaseUpdateExecutorService(parser, validation,
                versionService), mock(RFWLogger.class));
  }
}
