package br.com.rinos.app.testsupport.mysql;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Fornece um schema MySQL 9 descartável e isolado para testes de integração.
 *
 * <p>A instância externa somente é usada quando habilitada explicitamente no
 * {@code application.properties}. Sem essa configuração, a infraestrutura usa um contêiner e
 * ignora os testes quando Docker não está disponível. Toda criação e remoção permanece limitada
 * a um nome aleatório com o prefixo reservado {@code rinos_test_}.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class MySqlTestDatabase implements AutoCloseable {

  private static final String EXTERNAL_ENABLED_PROPERTY =
      "rinos.test-database.external.enabled";
  private static final String EXTERNAL_SERVER_URL_PROPERTY =
      "rinos.test-database.external.server-url";
  private static final String EXTERNAL_USERNAME_PROPERTY =
      "rinos.test-database.external.username";
  private static final String EXTERNAL_PASSWORD_PROPERTY =
      "rinos.test-database.external.password";

  private static final Path APPLICATION_PROPERTIES = Path.of("application.properties");
  private static final Pattern SCHEMA_NAME_PATTERN =
      Pattern.compile("rinos_test_[0-9a-f]{32}");
  private static final String CONTAINER_PASSWORD = "rinos-test";

  private final MySQLContainer container;
  private final String serverUrl;
  private final String username;
  private final String password;
  private final String schemaName;

  private MySqlTestDatabase(
      MySQLContainer container,
      String serverUrl,
      String username,
      String password) {
    this.container = container;
    this.serverUrl = validateServerUrl(serverUrl);
    this.username = requireText(username, EXTERNAL_USERNAME_PROPERTY);
    this.password = requireText(password, EXTERNAL_PASSWORD_PROPERTY);
    this.schemaName = "rinos_test_" + UUID.randomUUID().toString().replace("-", "");
    validateSchemaName(schemaName);
    validateServer();
  }

  /**
   * Abre a infraestrutura configurada ou inicia o fallback isolado do Testcontainers.
   *
   * @return banco de teste pronto ou vazio quando nem MySQL externo nem Docker estão disponíveis
   * @throws IllegalStateException quando a configuração externa ou o MySQL são incompatíveis
   */
  public static Optional<MySqlTestDatabase> openIfAvailable() {
    Properties properties = loadApplicationProperties();
    if (Boolean.parseBoolean(properties.getProperty(EXTERNAL_ENABLED_PROPERTY, "false"))) {
      return Optional.of(new MySqlTestDatabase(
          null,
          properties.getProperty(EXTERNAL_SERVER_URL_PROPERTY),
          properties.getProperty(EXTERNAL_USERNAME_PROPERTY),
          properties.getProperty(EXTERNAL_PASSWORD_PROPERTY)));
    }

    if (!DockerClientFactory.instance().isDockerAvailable()) {
      return Optional.empty();
    }
    MySQLContainer mysql = new MySQLContainer("mysql:9.0")
        .withDatabaseName("rinos_test_bootstrap")
        .withUsername("root")
        .withPassword(CONTAINER_PASSWORD);
    mysql.start();
    String containerServerUrl = "jdbc:mysql://"
        + mysql.getHost()
        + ":"
        + mysql.getMappedPort(MySQLContainer.MYSQL_PORT)
        + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    try {
      return Optional.of(new MySqlTestDatabase(
          mysql,
          containerServerUrl,
          mysql.getUsername(),
          mysql.getPassword()));
    } catch (RuntimeException exception) {
      mysql.stop();
      throw exception;
    }
  }

  /**
   * Recria o schema exclusivo da execução e devolve um {@link DataSource} selecionando-o.
   *
   * @return datasource conectado somente ao schema descartável
   * @throws IllegalStateException quando a preparação ou a seleção do schema falha
   */
  public DataSource recreateSchema() {
    validateSchemaName(schemaName);
    try (Connection connection = serverDataSource().getConnection();
        Statement statement = connection.createStatement()) {
      assertNoDatabaseSelected(connection);
      statement.execute("DROP DATABASE IF EXISTS `" + schemaName + "`");
      statement.execute("""
          CREATE DATABASE `%s`
          CHARACTER SET utf8mb4
          COLLATE utf8mb4_unicode_ci
          """.formatted(schemaName));
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Não foi possível recriar o schema MySQL descartável.", exception);
    }

    DriverManagerDataSource schemaDataSource = new DriverManagerDataSource(
        databaseUrl(serverUrl, schemaName),
        username,
        password);
    try (Connection connection = schemaDataSource.getConnection()) {
      String selectedSchema = selectedDatabase(connection);
      if (!schemaName.equals(selectedSchema)) {
        throw new IllegalStateException(
            "A conexão de teste não selecionou o schema descartável esperado.");
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Não foi possível validar o schema MySQL descartável.", exception);
    }
    return schemaDataSource;
  }

  /**
   * Remove o schema descartável e encerra o contêiner quando ele foi usado como provedor.
   *
   * @throws IllegalStateException quando o schema externo não pode ser removido
   */
  @Override
  public void close() {
    RuntimeException cleanupFailure = null;
    try {
      dropSchema();
    } catch (RuntimeException exception) {
      cleanupFailure = exception;
    } finally {
      if (container != null) {
        container.stop();
      }
    }
    if (cleanupFailure != null) {
      throw cleanupFailure;
    }
  }

  /**
   * Valida que a URL alcance somente o servidor MySQL, sem selecionar database.
   *
   * @param serverUrl URL JDBC administrativa
   * @return URL validada sem alterações
   * @throws IllegalStateException quando a URL não é MySQL ou seleciona um database
   */
  static String validateServerUrl(String serverUrl) {
    String candidate = requireText(serverUrl, EXTERNAL_SERVER_URL_PROPERTY);
    if (!candidate.startsWith("jdbc:mysql://")) {
      throw new IllegalStateException(
          EXTERNAL_SERVER_URL_PROPERTY + " deve ser uma URL JDBC MySQL.");
    }
    URI uri;
    try {
      uri = URI.create(candidate.substring("jdbc:".length()));
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          EXTERNAL_SERVER_URL_PROPERTY + " possui sintaxe inválida.", exception);
    }
    String path = uri.getPath();
    boolean emptyPath = path == null || path.isEmpty() || "/".equals(path);
    if (!"mysql".equalsIgnoreCase(uri.getScheme())
        || uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || !emptyPath) {
      throw new IllegalStateException(
          EXTERNAL_SERVER_URL_PROPERTY
              + " deve apontar para o servidor, sem selecionar um database.");
    }
    return candidate;
  }

  /**
   * Seleciona o schema seguro e fixa UTC para todas as conexões funcionais do teste.
   *
   * @param serverUrl URL JDBC administrativa sem database
   * @param schemaName nome interno validado
   * @return URL JDBC do schema descartável
   * @throws IllegalStateException quando a URL ou o schema violam as barreiras de segurança
   */
  static String databaseUrl(String serverUrl, String schemaName) {
    validateSchemaName(schemaName);
    String validatedServerUrl = validateServerUrl(serverUrl);
    int queryIndex = validatedServerUrl.indexOf('?');
    String endpoint = queryIndex < 0
        ? validatedServerUrl
        : validatedServerUrl.substring(0, queryIndex);
    String query = queryIndex < 0 ? "" : validatedServerUrl.substring(queryIndex);
    while (endpoint.endsWith("/")) {
      endpoint = endpoint.substring(0, endpoint.length() - 1);
    }
    String separator = query.isEmpty() ? "?" : "&";
    return endpoint
        + "/"
        + schemaName
        + query
        + separator
        + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
  }

  private static Properties loadApplicationProperties() {
    Properties properties = new Properties();
    if (!Files.exists(APPLICATION_PROPERTIES)) {
      return properties;
    }
    try (Reader reader = Files.newBufferedReader(
        APPLICATION_PROPERTIES,
        StandardCharsets.UTF_8)) {
      properties.load(reader);
      return properties;
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Não foi possível ler o application.properties para os testes MySQL.",
          exception);
    }
  }

  private void validateServer() {
    try (Connection connection = serverDataSource().getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT VERSION(), DATABASE()")) {
      if (!result.next() || result.getString(1) == null
          || !result.getString(1).startsWith("9.")) {
        throw new IllegalStateException("Os testes de integração exigem MySQL 9.x.");
      }
      if (result.getString(2) != null) {
        throw new IllegalStateException(
            "A conexão administrativa de teste não pode selecionar um database.");
      }
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Não foi possível validar a instância MySQL de testes.", exception);
    }
  }

  private void dropSchema() {
    validateSchemaName(schemaName);
    try (Connection connection = serverDataSource().getConnection();
        Statement statement = connection.createStatement()) {
      assertNoDatabaseSelected(connection);
      statement.execute("DROP DATABASE IF EXISTS `" + schemaName + "`");
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Não foi possível remover o schema MySQL descartável.", exception);
    }
  }

  private DataSource serverDataSource() {
    return new DriverManagerDataSource(serverUrl, username, password);
  }

  private static void assertNoDatabaseSelected(Connection connection) throws SQLException {
    String selectedSchema = selectedDatabase(connection);
    if (selectedSchema != null) {
      throw new IllegalStateException(
          "A conexão administrativa de teste selecionou um database inesperadamente.");
    }
  }

  private static String selectedDatabase(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
      if (!result.next()) {
        throw new IllegalStateException(
            "O MySQL não retornou o database selecionado pela conexão de teste.");
      }
      return result.getString(1);
    }
  }

  private static void validateSchemaName(String schemaName) {
    if (schemaName == null || !SCHEMA_NAME_PATTERN.matcher(schemaName).matches()) {
      throw new IllegalStateException(
          "Nome de schema recusado pela barreira de segurança dos testes.");
    }
  }

  private static String requireText(String value, String propertyName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "A propriedade " + propertyName + " é obrigatória para o MySQL externo de testes.");
    }
    return value.trim();
  }
}
