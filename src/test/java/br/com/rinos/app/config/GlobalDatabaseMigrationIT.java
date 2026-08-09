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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.database.config.RFWDatabaseAutoConfiguration;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateErrorCategoryEnum;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;

/**
 * Valida o bootstrap do catálogo global contra um MySQL 9 descartável.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-28
 */
@DisplayName("Migração do banco global")
class GlobalDatabaseMigrationIT {

  private static final String GLOBAL_UPDATE_LOCATIONS = String.join(",",
      "classpath:db/global/update/20260728_001_update.sql",
      "classpath:db/global/update/20260728_002_update.sql",
      "classpath:db/global/update/20260729_001_update.sql",
      "classpath:db/global/update/20260729_002_update.sql",
      "classpath:db/global/update/20260729_003_update.sql",
      "classpath:db/global/update/20260729_004_update.sql",
      "classpath:db/global/update/20260729_005_update.sql",
      "classpath:db/global/update/20260802_001_update.sql",
      "classpath:db/global/update/20260808_001_update.sql",
      "classpath:db/global/update/20260808_002_update.sql",
      "classpath:db/global/update/20260809_001_update.sql",
      "classpath:db/global/update/20260809_002_update.sql");
  private static final String TARGET_VERSION = "20260809002";

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
   * Prepara um schema vazio e independente antes de cada cenário.
   */
  @BeforeEach
  void resetDatabase() {
    Assumptions.assumeTrue(
        testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
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
    assertThat(tableExists("identity_user")).isTrue();
    assertThat(tableExists("identity_registration")).isTrue();
    assertThat(tableExists("identity_localCredential")).isTrue();
    assertThat(tableExists("identity_passwordRecovery")).isTrue();
    assertThat(tableExists("identity_verification")).isTrue();
    assertThat(tableExists("identity_legalDocumentVersion")).isTrue();
    assertThat(tableExists("identity_legalConsent")).isTrue();
    assertThat(tableExists("identity_externalIdentity")).isTrue();
    assertAuthenticationTables();
    assertThat(tableExists("security_originWindow")).isTrue();
    assertThat(tableExists("identity_event")).isTrue();
    assertThat(tableExists("testGlobalMigrationMarker")).isFalse();
    assertIdentitySchemaHasNoTenantReferences();
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
    assertThat(tableExists("identity_user")).isTrue();
    assertThat(tableExists("identity_registration")).isTrue();
    assertThat(tableExists("identity_localCredential")).isTrue();
    assertThat(tableExists("identity_passwordRecovery")).isTrue();
    assertThat(tableExists("identity_verification")).isTrue();
    assertThat(tableExists("identity_legalDocumentVersion")).isTrue();
    assertThat(tableExists("identity_legalConsent")).isTrue();
    assertThat(tableExists("identity_externalIdentity")).isTrue();
    assertAuthenticationTables();
    assertThat(tableExists("security_originWindow")).isTrue();
    assertThat(tableExists("identity_event")).isTrue();
    assertThat(readVersion()).isEqualTo(TARGET_VERSION);
    assertIdentitySchemaHasNoTenantReferences();
    assertMaintenanceLeaseSchema();
  }

  /**
   * Comprova que a falha interrompe o catálogo e não publica versão falsa.
   *
   * <p>DDL anterior pode permanecer no MySQL; por isso o contrato exige intervenção externa em
   * vez de prometer rollback total.
   *
   * @throws SQLException quando o estado parcial não pode ser consultado
   */
  @Test
  void startup_shouldStopWithoutAdvancingVersion_whenUpdateFailsPartially()
      throws SQLException {
    initializeDatabase();
    String locations = GLOBAL_UPDATE_LOCATIONS
        + ",classpath:db/global/failure/20260809_003_update.sql";

    contextRunner(locations).run(context -> {
      assertThat(context).hasFailed();
      assertFailureCategory(
          context.getStartupFailure(),
          RFWDatabaseUpdateErrorCategoryEnum.EXECUTION);
    });

    assertThat(readVersion()).isEqualTo(TARGET_VERSION);
    assertThat(tableExists("testFailedUpdateMarker")).isTrue();
    assertThat(tableExists("testUnexpectedUpdateContinuation")).isFalse();
  }

  /**
   * Comprova a evolução preservando credenciais criadas na versão anterior.
   *
   * @throws SQLException quando o cenário ou seus metadados não podem ser consultados
   */
  @Test
  void startup_shouldBackfillCredentialDates_whenUpdatingPreviousSchema() throws SQLException {
    initializePreviousDatabase();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("""
          INSERT INTO identity_user (email, normalizedEmail, status)
          VALUES ('existing@example.com', 'existing@example.com', 'ACTIVE')
          """);
      statement.executeUpdate("""
          INSERT INTO identity_localCredential (idUser, passwordHash, status)
          VALUES (1, '{argon2id}existing', 'ACTIVE')
          """);
      statement.executeUpdate("""
          INSERT INTO identity_externalIdentity
            (idUser, provider, issuer, subject, status, verifiedAt, activatedAt)
          VALUES
            (1, 'GOOGLE', 'https://accounts.google.com', 'subject-existing', 'ACTIVE',
             CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
          """);
    }

    runUpdater();

    assertThat(readVersion()).isEqualTo(TARGET_VERSION);
    assertThat(readTimestamp("identity_localCredential", "passwordChangedAt", 1L)).isNotNull();
    assertThat(readTimestamp("identity_localCredential", "compromisedAt", 1L)).isNull();
    assertThat(readTimestamp("identity_externalIdentity", "lastUsedAt", 1L)).isNull();
    assertAuthenticationTables();
  }

  /**
   * Cria o contexto mínimo que reproduz o bootstrap automático do updater global.
   *
   * @return executor descartável de contexto Spring
   */
  private ApplicationContextRunner contextRunner() {
    return contextRunner(GLOBAL_UPDATE_LOCATIONS);
  }

  /**
   * Cria o contexto mínimo com um catálogo explicitamente delimitado.
   *
   * @param locations locations globais a descobrir
   * @return executor descartável de contexto Spring
   */
  private ApplicationContextRunner contextRunner(String locations) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            RFWLoggingAutoConfiguration.class,
            RFWDatabaseAutoConfiguration.class))
        .withPropertyValues(
            "rfw.database.update.enabled=true",
            "rfw.database.update.locations=" + locations,
            "rfw.database.update.lock-timeout=30s")
        .withBean(DataSource.class, () -> dataSource);
  }

  /**
   * Confirma que a identidade global não introduziu tabela ou coluna de tenant.
   *
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private void assertIdentitySchemaHasNoTenantReferences() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name REGEXP '^(identity|security)_'
              AND LOWER(column_name) = 'tenantid'
            """);
        ResultSet result = statement.executeQuery()) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isZero();
    }
    assertThat(tableExists("tenant_user")).isFalse();
    assertThat(tableExists("account_user")).isFalse();
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
   * Reproduz o schema imediatamente anterior sem modificar scripts incrementais publicados.
   */
  private void initializePreviousDatabase() {
    initializeLegacyDatabase();
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("db/global/update/20260728_002_update.sql"),
        new ClassPathResource("db/global/update/20260729_001_update.sql"),
        new ClassPathResource("db/global/update/20260729_002_update.sql"),
        new ClassPathResource("db/global/update/20260729_003_update.sql"),
        new ClassPathResource("db/global/update/20260729_004_update.sql"),
        new ClassPathResource("db/global/update/20260729_005_update.sql"),
        new ClassPathResource("db/global/update/20260802_001_update.sql"));
    populator.execute(dataSource);
  }

  /**
   * Lê uma data opcional de um registro conhecido do cenário descartável.
   *
   * @param tableName tabela global validada pelo teste
   * @param columnName coluna temporal validada pelo teste
   * @param id identificador interno preparado no cenário
   * @return instante JDBC ou {@code null}
   * @throws SQLException quando a consulta falha
   */
  private java.sql.Timestamp readTimestamp(String tableName, String columnName, long id)
      throws SQLException {
    String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE id = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getTimestamp(1);
      }
    }
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
   * Confirma a presença de todas as estruturas globais introduzidas para autenticação.
   *
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private void assertAuthenticationTables() throws SQLException {
    assertThat(List.of(
        "identity_authenticationFlow",
        "identity_authenticationFlowMethod",
        "identity_authenticationProof",
        "identity_totpFactor",
        "identity_emailFactor",
        "identity_recoveryCodeSet",
        "identity_recoveryCode",
        "identity_passkeyUser",
        "identity_passkeyCredential",
        "identity_authSession",
        "identity_authSessionMethod",
        "identity_reauthenticationContext",
        "security_authenticationWindow"))
        .allMatch(tableName -> {
          try {
            return tableExists(tableName);
          } catch (SQLException exception) {
            throw new IllegalStateException(exception);
          }
        });
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
    Throwable current = startupFailure;
    while (current != null && !(current instanceof RFWDatabaseUpdateException)) {
      current = current.getCause();
    }
    assertThat(current).isInstanceOf(RFWDatabaseUpdateException.class);
    assertThat(((RFWDatabaseUpdateException) current).getCategory())
        .isEqualTo(expectedCategory);
  }
}
