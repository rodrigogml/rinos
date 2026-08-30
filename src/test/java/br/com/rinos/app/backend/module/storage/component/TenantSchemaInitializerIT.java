package br.com.rinos.app.backend.module.storage.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.AbstractDataSource;

import com.zaxxer.hikari.HikariDataSource;

import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;
import br.com.rinos.app.backend.module.storage.vo.TenantSchemaInitializationResultVO;
import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;
import br.eng.rodrigogml.rfw.database.config.DatabaseUpdatePropertiesConfig;
import br.eng.rodrigogml.rfw.database.service.DatabaseUpdateStrategyResolverService;
import br.eng.rodrigogml.rfw.database.service.DatabaseVersionService;
import br.eng.rodrigogml.rfw.database.strategy.MySQLDatabaseUpdateStrategy;
import br.eng.rodrigogml.rfw.exception.RFWDatabaseUpdateException;

/**
 * Valida o provisionamento físico idempotente de um tenant contra MySQL 9 controlado.
 *
 * <p>O cenário usa um único schema físico fixo e autorizado exclusivamente para a classe. O teste nunca seleciona,
 * consulta ou altera o catálogo global.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@DisplayName("Inicialização física do schema tenant")
class TenantSchemaInitializerIT {

  private static final TenantPhysicalIdentifier PHYSICAL_IDENTIFIER =
      new TenantPhysicalIdentifier("0f7c22fb0eaa4ea8ad9a5e1f0b730001");
  private static final String SCHEMA_NAME = PHYSICAL_IDENTIFIER.schemaName();
  private static final String EXPECTED_VERSION = "20260829001";

  private static MySqlTestDatabase testDatabase;

  private DataSource serverDataSource;

  /** Reserva a infraestrutura externa exclusiva antes dos cenários físicos. */
  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  /** Garante que o schema físico reservado não sobreviva ao término da classe. */
  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      dropReservedSchema(testDatabase.createServerDataSource());
      testDatabase.close();
    }
  }

  /** Remove apenas o schema físico reservado antes de cada cenário. */
  @BeforeEach
  void resetSchema() {
    Assumptions.assumeTrue(testDatabase != null, "MySQL de teste indisponível");
    serverDataSource = testDatabase.createServerDataSource();
    dropReservedSchema(serverDataSource);
  }

  /** Mantém a instância local limpa quando um cenário falha depois de criar o schema. */
  @AfterEach
  void removeSchema() {
    if (serverDataSource != null) {
      dropReservedSchema(serverDataSource);
    }
  }

  /** Confirma a criação real, o init e a versão estrutural esperada para um tenant novo. */
  @Test
  void initialize_shouldCreateAndVerifyPhysicalTenantSchema_whenSchemaIsAbsent() throws SQLException {
    try (InitializerHarness harness = harness(serverDataSource)) {
      TenantSchemaInitializationResultVO result = harness.initializer().initialize(PHYSICAL_IDENTIFIER,
          EXPECTED_VERSION);

      assertThat(result.createdNow()).isTrue();
      assertThat(schemaExists(serverDataSource)).isTrue();
      assertThat(readBootstrapRows(harness.tenantDataSourceFactory())).isEqualTo(1);
      assertThat(readVersion(harness.tenantDataSourceFactory())).isEqualTo(EXPECTED_VERSION);
    }
  }

  /**
   * Confirma que uma nova instância após reinício e um replay subsequente não reexecutam os dados iniciais.
   */
  @Test
  void initialize_shouldKeepBootstrapSingle_whenWorkerRestartsAndReplaysProvisioning() throws SQLException {
    try (InitializerHarness firstWorker = harness(serverDataSource)) {
      assertThat(firstWorker.initializer().initialize(PHYSICAL_IDENTIFIER, EXPECTED_VERSION).createdNow()).isTrue();
    }

    try (InitializerHarness restartedWorker = harness(serverDataSource)) {
      assertThat(restartedWorker.initializer().initialize(PHYSICAL_IDENTIFIER, EXPECTED_VERSION).createdNow()).isFalse();
      assertThat(restartedWorker.initializer().initialize(PHYSICAL_IDENTIFIER, EXPECTED_VERSION).createdNow()).isFalse();
      assertThat(readBootstrapRows(restartedWorker.tenantDataSourceFactory())).isEqualTo(1);
      assertThat(readVersion(restartedWorker.tenantDataSourceFactory())).isEqualTo(EXPECTED_VERSION);
    }
  }

  /** Confirma que indisponibilidade de DDL não deixa um schema parcial e é exposta ao coordenador. */
  @Test
  void initialize_shouldRejectAndLeaveSchemaAbsent_whenCreateDatabaseIsUnavailable() {
    DataSource unavailableDdlDataSource = rejectCreateDatabase(serverDataSource);

    try (InitializerHarness harness = harness(unavailableDdlDataSource)) {
      assertThatThrownBy(() -> harness.initializer().initialize(PHYSICAL_IDENTIFIER, EXPECTED_VERSION))
          .isInstanceOf(RFWDatabaseUpdateException.class)
          .hasMessageContaining("criar ou observar");
      assertThat(schemaExists(serverDataSource)).isFalse();
    }
  }

  /**
   * Monta componentes reais de init, verificação, lock e datasource para o worker sob teste.
   *
   * @param initializationDataSource conexão administrativa normal ou a simulação de indisponibilidade de DDL
   * @return conjunto fechável de componentes exclusivo do cenário
   */
  private static InitializerHarness harness(DataSource initializationDataSource) {
    HikariDataSource globalCatalogDataSource = testDatabase.createLazyGlobalCatalogDataSource();
    TenantDataSourceFactory tenantDataSourceFactory = new TenantDataSourceFactory(globalCatalogDataSource);
    DatabaseUpdatePropertiesConfig properties = new DatabaseUpdatePropertiesConfig();
    properties.setLockTimeout(Duration.ofSeconds(30));
    TenantDatabaseStructureVerifier verifier = new TenantDatabaseStructureVerifier(new DatabaseVersionService(),
        TenantDatabaseCatalogServiceTest.officialCatalogService());
    TenantStorageNamedLockComponent namedLock = new TenantStorageNamedLockComponent(
        new DatabaseUpdateStrategyResolverService(List.of(new MySQLDatabaseUpdateStrategy())));
    TenantSchemaInitializer initializer = new TenantSchemaInitializer(initializationDataSource, tenantDataSourceFactory,
        new TenantSchemaInitScriptComponent(new PathMatchingResourcePatternResolver()), verifier, namedLock,
        properties);
    return new InitializerHarness(initializer, tenantDataSourceFactory, globalCatalogDataSource);
  }

  /**
   * Conta o único marco de bootstrap que o init pode inserir no schema físico reservado.
   *
   * @param tenantDataSourceFactory factory real do datasource físico do tenant
   * @return quantidade de linhas para a chave de baseline
   * @throws SQLException quando a consulta de validação falhar
   */
  private static int readBootstrapRows(TenantDataSourceFactory tenantDataSourceFactory) throws SQLException {
    try (HikariDataSource tenantDataSource = tenantDataSourceFactory.create(PHYSICAL_IDENTIFIER);
        Connection connection = tenantDataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM core_tenantBootstrap
            WHERE bootstrapKey = 'tenant.schema.baseline'
            """);
        ResultSet result = statement.executeQuery()) {
      assertThat(result.next()).isTrue();
      return result.getInt(1);
    }
  }

  /**
   * Lê a versão estrutural da view exigida pela RFW no tenant provisionado.
   *
   * @param tenantDataSourceFactory factory real do datasource físico do tenant
   * @return versão exposta pela view do schema
   * @throws SQLException quando a leitura da view falhar
   */
  private static String readVersion(TenantDataSourceFactory tenantDataSourceFactory) throws SQLException {
    try (HikariDataSource tenantDataSource = tenantDataSourceFactory.create(PHYSICAL_IDENTIFIER);
        Connection connection = tenantDataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT version FROM databaseVersion")) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  /**
   * Verifica a presença do único schema físico reservado sem selecionar outro catálogo.
   *
   * @param dataSource conexão administrativa do MySQL de testes
   * @return {@code true} quando o schema reservado existe
   */
  private static boolean schemaExists(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT 1
            FROM information_schema.schemata
            WHERE schema_name = ?
            """)) {
      statement.setString(1, SCHEMA_NAME);
      return statement.executeQuery().next();
    } catch (SQLException exception) {
      throw new IllegalStateException("Não foi possível verificar o schema físico reservado.", exception);
    }
  }

  /**
   * Remove somente o schema físico literal reservado para a classe de integração.
   *
   * @param dataSource conexão administrativa do MySQL de testes
   * @throws IllegalStateException quando a limpeza controlada falhar
   */
  private static void dropReservedSchema(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS `" + SCHEMA_NAME + "`");
    } catch (SQLException exception) {
      throw new IllegalStateException("Não foi possível remover o schema físico reservado.", exception);
    }
  }

  /**
   * Produz datasource que preserva consultas reais, mas simula indisponibilidade somente no comando de criação.
   *
   * @param delegate datasource administrativo real e controlado
   * @return datasource que recusa {@code CREATE DATABASE}
   */
  private static DataSource rejectCreateDatabase(DataSource delegate) {
    return new AbstractDataSource() {
      @Override
      public Connection getConnection() throws SQLException {
        return rejectingConnection(delegate.getConnection());
      }

      @Override
      public Connection getConnection(String username, String password) throws SQLException {
        return rejectingConnection(delegate.getConnection(username, password));
      }
    };
  }

  /**
   * Encapsula a conexão para interceptar exclusivamente a obtenção de statements sem parâmetro.
   *
   * @param delegate conexão JDBC real
   * @return proxy JDBC com a falha de DDL controlada
   */
  private static Connection rejectingConnection(Connection delegate) {
    InvocationHandler handler = (proxy, method, arguments) -> {
      if ("createStatement".equals(method.getName())) {
        Statement statement = (Statement) invoke(method, delegate, arguments);
        return rejectingStatement(statement);
      }
      return invoke(method, delegate, arguments);
    };
    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
        handler);
  }

  /**
   * Encapsula um statement e falha apenas para o comando de criação do schema físico.
   *
   * @param delegate statement JDBC real
   * @return proxy JDBC que simula indisponibilidade de DDL
   */
  private static Statement rejectingStatement(Statement delegate) {
    InvocationHandler handler = (proxy, method, arguments) -> {
      if ("execute".equals(method.getName()) && arguments != null && arguments.length > 0
          && arguments[0] instanceof String sql && sql.startsWith("CREATE DATABASE")) {
        throw new SQLException("simulated DDL unavailable");
      }
      return invoke(method, delegate, arguments);
    };
    return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[] {Statement.class},
        handler);
  }

  /**
   * Propaga a chamada refletida preservando a causa JDBC original para o código em teste.
   *
   * @param method método interceptado
   * @param target alvo JDBC real
   * @param arguments argumentos originais, possivelmente ausentes
   * @return resultado produzido pelo alvo
   * @throws Throwable quando o alvo rejeitar a operação
   */
  private static Object invoke(Method method, Object target, Object[] arguments) throws Throwable {
    try {
      return method.invoke(target, arguments);
    } catch (InvocationTargetException exception) {
      throw exception.getCause();
    }
  }

  /**
   * Mantém os componentes de infraestrutura que precisam ser encerrados ao fim de cada cenário físico.
   *
   * @param initializer inicializador sob teste
   * @param tenantDataSourceFactory factory do schema físico reservado
   * @param globalCatalogDataSource fonte-base preguiçosa usada exclusivamente pela factory
   */
  private record InitializerHarness(TenantSchemaInitializer initializer,
      TenantDataSourceFactory tenantDataSourceFactory, HikariDataSource globalCatalogDataSource)
      implements AutoCloseable {

    @Override
    public void close() {
      globalCatalogDataSource.close();
    }
  }
}
