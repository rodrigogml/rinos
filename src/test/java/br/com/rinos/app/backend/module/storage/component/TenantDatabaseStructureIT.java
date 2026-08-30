package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseMigrationEvidenceVO;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseUpdateScriptVO;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.database.config.RFWDatabaseAutoConfiguration;
import br.eng.rodrigogml.rfw.database.service.DatabaseVersionService;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;
import br.eng.rodrigogml.rfw.logging.config.RFWLoggingAutoConfiguration;

/**
 * Valida o init, a atualização equivalente e a verificação de estrutura do catálogo tenant em MySQL 9 controlado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-29
 */
@DisplayName("Estrutura do banco de tenant")
class TenantDatabaseStructureIT {

  private static final ApplicationContextRunner UPDATE_CONTEXT = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(
          RFWLoggingAutoConfiguration.class,
          RFWDatabaseAutoConfiguration.class));

  private static MySqlTestDatabase testDatabase;

  private DataSource dataSource;

  /** Reserva a infraestrutura descartável usada pela classe. */
  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  /** Remove o schema descartável e encerra o provedor eventual. */
  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  /** Prepara um schema novo antes de cada cenário. */
  @BeforeEach
  void resetDatabase() {
    Assumptions.assumeTrue(testDatabase != null, "MySQL de teste indisponível");
    dataSource = testDatabase.recreateSchema();
  }

  /** Confirma que o init cria somente a estrutura técnica mínima e o marco final do catálogo tenant. */
  @Test
  void init_shouldCreateStableTenantBootstrap_whenSchemaIsNew() throws SQLException {
    new ResourceDatabasePopulator(
        new ClassPathResource("db/tenant/init/01-ddl.sql"),
        new ClassPathResource("db/tenant/init/02-seed.sql"),
        new ClassPathResource("db/tenant/init/03-procedures.sql"),
        new ClassPathResource("db/tenant/init/99-database-version.sql"))
            .execute(dataSource);

    assertThat(tableExists("core_tenantBootstrap")).isTrue();
    assertThat(readBaseline()).isEqualTo("20260829001");
    assertThat(readVersion()).isEqualTo("20260829001");
    assertThat(tableExists("identity_user")).isFalse();
  }

  /** Confirma que o RFW executa somente o update de tenant e atinge o mesmo estado do init. */
  @Test
  void update_shouldCreateEquivalentTenantStructure_whenSchemaStartsAtZeroVersion() throws SQLException {
    replaceVersion("00000000000");

    UPDATE_CONTEXT
        .withPropertyValues(
            "rfw.database.update.enabled=true",
            "rfw.database.update.locations=classpath:db/tenant/update/",
            "rfw.database.update.lock-timeout=30s")
        .withBean(DataSource.class, () -> dataSource)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(tableExists("core_tenantBootstrap")).isTrue();
    assertThat(readBaseline()).isEqualTo("20260829001");
    assertThat(readVersion()).isEqualTo("20260829001");
    assertThat(tableExists("identity_user")).isFalse();
  }

  /** Confirma a detecção de lacuna e adulteração no histórico posterior ao baseline. */
  @Test
  void verify_shouldRejectMissingOrTamperedEvidence_whenKnownUpdateFollowsBaseline() throws SQLException {
    createControlledVerificationSchema();
    TenantDatabaseCatalogService catalogService = TenantDatabaseCatalogServiceTest.controlledCatalogService();
    TenantDatabaseStructureVerifier verifier = new TenantDatabaseStructureVerifier(
        new DatabaseVersionService(), catalogService);
    TenantDatabaseCatalogVO catalog = catalogService.inspect();
    TenantDatabaseUpdateScriptVO update = catalog.scripts().getLast();

    assertThatThrownBy(() -> verifier.verify(dataSource, "20260831001", List.of()))
        .isInstanceOf(RFWDatabaseUpdateException.class);
    assertThatThrownBy(() -> verifier.verify(dataSource, "20260831001", List.of(
        new TenantDatabaseMigrationEvidenceVO(update.fileName(), update.version(), new byte[32]))))
            .isInstanceOf(RFWDatabaseUpdateException.class);

    assertThat(verifier.verify(dataSource, "20260831001", List.of(
        new TenantDatabaseMigrationEvidenceVO(update.fileName(), update.version(), update.contentHash()))))
            .extracting(TenantDatabaseCatalogVO::targetVersion)
            .isEqualTo(catalog.targetVersion());
  }

  /** Cria a estrutura mínima que representa um tenant inicializado antes do segundo update controlado. */
  private void createControlledVerificationSchema() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE core_tenantBootstrap (
            id BIGINT NOT NULL,
            bootstrapKey VARCHAR(100) NOT NULL,
            bootstrapValue VARCHAR(100) NOT NULL,
            CONSTRAINT pk_core_tenant_bootstrap PRIMARY KEY (id),
            CONSTRAINT uk_core_tenant_bootstrap_key UNIQUE (bootstrapKey)
          ) ENGINE = InnoDB
          """);
      statement.execute("""
          INSERT INTO core_tenantBootstrap (id, bootstrapKey, bootstrapValue)
          VALUES (1, 'tenant.schema.baseline', '20260830001')
          """);
    }
    replaceVersion("20260831001");
  }

  /** Substitui a view de versão apenas no schema descartável da execução. */
  private void replaceVersion(String version) {
    new ResourceDatabasePopulator(new ByteArrayResource(("""
        CREATE OR REPLACE
        SQL SECURITY INVOKER
        VIEW databaseVersion AS
        SELECT '%s' AS version;
        """).formatted(version).getBytes(StandardCharsets.UTF_8))).execute(dataSource);
  }

  /** Consulta a versão exposta pela view requerida pela RFW. */
  private String readVersion() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT version FROM databaseVersion")) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  /** Consulta o marco técnico imutável criado pelo init do tenant. */
  private String readBaseline() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT bootstrapValue FROM core_tenantBootstrap WHERE bootstrapKey = ?
            """)) {
      statement.setString(1, "tenant.schema.baseline");
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getString(1);
      }
    }
  }

  /** Verifica existência de tabela sem depender de metadados específicos do driver. */
  private boolean tableExists(String tableName) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = ?
            """)) {
      statement.setString(1, tableName);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getInt(1) == 1;
      }
    }
  }
}
