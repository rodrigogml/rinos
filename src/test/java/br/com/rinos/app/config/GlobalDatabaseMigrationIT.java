package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import br.eng.rodrigogml.rfw.platform.autoconfig.RFWPlatformAutoConfiguration;
import br.eng.rodrigogml.rfw.platform.shared.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.platform.shared.exception.RFWDatabaseUpdateException;

/**
 * Valida o bootstrap do catálogo global contra um MySQL 9 descartável.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-28
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Migração do banco global")
class GlobalDatabaseMigrationIT {

  private static final String GLOBAL_UPDATE_LOCATIONS = String.join(",",
      "classpath:db/global/update/20260728_001_update.sql",
      "classpath:db/global/update/20260728_002_update.sql");
  private static final String TARGET_VERSION = "20260728002";

  @Container
  private static final MySQLContainer MYSQL = new MySQLContainer("mysql:9.0")
      .withDatabaseName("rinos_global")
      .withUsername("rinos")
      .withPassword("rinos-test");

  private DataSource dataSource;

  /**
   * Prepara um schema vazio e independente antes de cada cenário.
   *
   * @throws SQLException quando o schema descartável não pode ser limpo
   */
  @BeforeEach
  void resetDatabase() throws SQLException {
    dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP VIEW IF EXISTS databaseVersion");
      statement.execute("DROP TABLE IF EXISTS platform_maintenanceLease");
      statement.execute("DROP TABLE IF EXISTS testGlobalMigrationMarker");
      statement.execute("DROP TABLE IF EXISTS testTenantMigrationMarker");
    }
  }

  /**
   * Comprova que um banco sem init não pode concluir o refresh.
   */
  @Test
  void startup_shouldFail_whenGlobalDatabaseIsEmpty() {
    contextRunner().run(context -> {
      assertThat(context).hasFailed();
      assertFailureCategory(context.getStartupFailure(),
          RFWDatabaseUpdateErrorCategoryEnum.VERSION_CONSISTENCY);
    });
  }

  /**
   * Comprova que um banco já atualizado não reaplica scripts.
   *
   * @throws SQLException quando o estado final não pode ser consultado
   */
  @Test
  void startup_shouldNotReapplyUpdate_whenGlobalDatabaseIsCurrent() throws SQLException {
    initializeDatabase();
    runUpdater();
    runUpdater();

    assertThat(readVersion()).isEqualTo(TARGET_VERSION);
    assertThat(tableExists("platform_maintenanceLease")).isTrue();
    assertThat(tableExists("testGlobalMigrationMarker")).isFalse();
    assertMaintenanceLeaseSchema();
  }

  /**
   * Comprova que uma versão posterior aos artefatos bloqueia o refresh.
   *
   * @throws SQLException quando a versão incompatível não pode ser preparada
   */
  @Test
  void startup_shouldFail_whenGlobalDatabaseVersionIsNewerThanArtifacts() throws SQLException {
    initializeDatabase();
    replaceVersionWithNewerArtifact();

    contextRunner().run(context -> {
      assertThat(context).hasFailed();
      assertFailureCategory(context.getStartupFailure(),
          RFWDatabaseUpdateErrorCategoryEnum.VERSION_CONSISTENCY);
    });
  }

  /**
   * Comprova que o bootstrap global não descobre nem executa o catálogo de tenant.
   *
   * @throws SQLException quando o estado final não pode ser consultado
   */
  @Test
  void startup_shouldUseOnlyGlobalCatalog_whenTenantCatalogIsAlsoPresent() throws SQLException {
    initializeLegacyDatabase();

    runUpdater();

    assertThat(tableExists("testGlobalMigrationMarker")).isTrue();
    assertThat(tableExists("testTenantMigrationMarker")).isFalse();
    assertThat(tableExists("platform_maintenanceLease")).isTrue();
    assertThat(readVersion()).isEqualTo(TARGET_VERSION);
    assertMaintenanceLeaseSchema();
  }

  /**
   * Cria o contexto mínimo que reproduz o bootstrap automático do updater global.
   *
   * @return executor descartável de contexto Spring
   */
  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RFWPlatformAutoConfiguration.class))
        .withPropertyValues(
            "rfw.platform.database.update.enabled=true",
            "rfw.platform.database.update.locations=" + GLOBAL_UPDATE_LOCATIONS,
            "rfw.platform.database.update.lock-timeout=30s")
        .withBean(DataSource.class, () -> dataSource);
  }

  /**
   * Executa o updater e exige refresh concluído sem falhas.
   */
  private void runUpdater() {
    contextRunner().run(context -> assertThat(context).hasNotFailed());
  }

  /**
   * Executa o catálogo real de init global na ordem operacional documentada.
   */
  private void initializeDatabase() {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"),
        new ClassPathResource("db/global/init/02-seed.sql"),
        new ClassPathResource("db/global/init/03-procedures.sql"),
        new ClassPathResource("db/global/init/99-database-version.sql"));
    populator.execute(dataSource);
  }

  /**
   * Cria somente o marco legado necessário para exercitar todos os updates disponíveis.
   */
  private void initializeLegacyDatabase() {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ByteArrayResource("""
        CREATE OR REPLACE
        SQL SECURITY INVOKER
        VIEW databaseVersion AS
        SELECT '00000000000' AS version;
        """.getBytes(StandardCharsets.UTF_8)));
    populator.execute(dataSource);
  }

  /**
   * Simula banco criado por artefato posterior ao catálogo disponível.
   *
   * @throws SQLException quando a view de versão não pode ser substituída
   */
  private void replaceVersionWithNewerArtifact() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("""
            CREATE OR REPLACE
            SQL SECURITY INVOKER
            VIEW databaseVersion AS
            SELECT '99999999999' AS version
            """);
    }
  }

  /**
   * Lê o marco de versão persistido no banco descartável.
   *
   * @return versão compacta exposta pela view
   * @throws SQLException quando a versão não pode ser consultada
   */
  private String readVersion() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT version FROM databaseVersion")) {
      assertThat(result.next()).isTrue();
      return result.getString("version");
    }
  }

  /**
   * Verifica se uma tabela pertence ao schema corrente.
   *
   * @param tableName nome físico exato da tabela
   * @return {@code true} quando a tabela existe no schema global descartável
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private boolean tableExists(String tableName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = ?
            """)) {
      statement.setString(1, tableName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getInt(1) == 1;
      }
    }
  }

  /**
   * Comprova nomes, ordem e unicidade do contrato físico do lease.
   *
   * @throws SQLException quando os metadados do schema não podem ser consultados
   */
  private void assertMaintenanceLeaseSchema() throws SQLException {
    assertThat(readColumnNames("platform_maintenanceLease")).containsExactly(
        "id",
        "leaseKey",
        "instanceId",
        "sessionId",
        "epoch",
        "acquiredAt",
        "heartbeatAt",
        "leaseUntil",
        "createdAt",
        "updatedAt",
        "version");
    assertThat(uniqueIndexExists(
        "platform_maintenanceLease",
        "uk_platform_maintenance_lease_key",
        "leaseKey")).isTrue();
  }

  /**
   * Lê as colunas físicas na ordem declarada pelo catálogo.
   *
   * @param tableName nome físico exato da tabela
   * @return nomes das colunas em ordem ordinal
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private List<String> readColumnNames(String tableName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
            ORDER BY ordinal_position
            """)) {
      statement.setString(1, tableName);
      try (ResultSet result = statement.executeQuery()) {
        List<String> columns = new ArrayList<>();
        while (result.next()) {
          columns.add(result.getString("column_name"));
        }
        return columns;
      }
    }
  }

  /**
   * Verifica a constraint única que protege a identidade lógica do lease.
   *
   * @param tableName nome físico exato da tabela
   * @param indexName nome físico exato do índice
   * @param columnName coluna protegida
   * @return {@code true} quando o índice único existe para a coluna
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private boolean uniqueIndexExists(String tableName, String indexName, String columnName)
      throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND index_name = ?
              AND column_name = ?
              AND non_unique = 0
            """)) {
      statement.setString(1, tableName);
      statement.setString(2, indexName);
      statement.setString(3, columnName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getInt(1) == 1;
      }
    }
  }

  /**
   * Confirma a categoria RFW na causa raiz de uma falha de refresh.
   *
   * @param startupFailure falha capturada pelo executor de contexto
   * @param expectedCategory categoria operacional esperada
   */
  private void assertFailureCategory(Throwable startupFailure,
      RFWDatabaseUpdateErrorCategoryEnum expectedCategory) {
    Throwable rootCause = NestedExceptionUtils.getMostSpecificCause(startupFailure);
    assertThat(rootCause).isInstanceOf(RFWDatabaseUpdateException.class);
    assertThat(((RFWDatabaseUpdateException) rootCause).getCategory())
        .isEqualTo(expectedCategory);
  }
}
