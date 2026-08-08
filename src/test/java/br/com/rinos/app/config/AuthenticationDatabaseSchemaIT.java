package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

/**
 * Valida equivalência, constraints e tipos físicos do modelo global de autenticação no MySQL 9.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
@DisplayName("Schema global de autenticação")
class AuthenticationDatabaseSchemaIT {

  private static final List<String> AUTHENTICATION_TABLES = List.of(
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
      "security_authenticationWindow");

  private static MySqlTestDatabase testDatabase;

  private DataSource dataSource;

  /**
   * Seleciona a instância MySQL 9 configurada e reserva seu schema descartável.
   */
  @BeforeAll
  static void startDatabase() {
    testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
  }

  /**
   * Remove o schema descartável e encerra eventual contêiner.
   */
  @AfterAll
  static void stopDatabase() {
    if (testDatabase != null) {
      testDatabase.close();
    }
  }

  /**
   * Inicializa do zero o schema consolidado antes de cada cenário.
   */
  @BeforeEach
  void initializeSchema() {
    Assumptions.assumeTrue(testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    dataSource = testDatabase.recreateSchema();
    executeInit(dataSource);
  }

  /**
   * Compara o DDL materializado pelo init com o obtido ao aplicar toda a cadeia incremental.
   *
   * @throws SQLException quando o catálogo não pode ser inspecionado
   */
  @Test
  void initAndUpdate_shouldProduceEquivalentAuthenticationSchema() throws SQLException {
    Map<String, String> initializedDefinitions = readTableDefinitions(dataSource);

    dataSource = testDatabase.recreateSchema();
    executeUpdates(dataSource);

    assertThat(readVersion(dataSource)).isEqualTo("20260808002");
    assertThat(readTableDefinitions(dataSource)).isEqualTo(initializedDefinitions);
  }

  /**
   * Comprova que o update não transforma métodos históricos em fatores comprovados.
   *
   * @throws SQLException quando o estado migrado não pode ser consultado
   */
  @Test
  void flowMethodUpdate_shouldBackfillExistingRowsAsPermitted() throws SQLException {
    dataSource = testDatabase.recreateSchema();
    new ResourceDatabasePopulator(new ByteArrayResource("""
        CREATE TABLE identity_authenticationFlowMethod (
          id BIGINT AUTO_INCREMENT NOT NULL,
          idAuthenticationFlow BIGINT NOT NULL,
          method VARCHAR(32) NOT NULL,
          createdAt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
          CONSTRAINT pk_identity_authentication_flow_method PRIMARY KEY (id)
        ) ENGINE = InnoDB;
        INSERT INTO identity_authenticationFlowMethod (idAuthenticationFlow, method)
        VALUES (10, 'PASSWORD');
        """.getBytes(StandardCharsets.UTF_8)),
        new ClassPathResource("db/global/update/20260808_002_update.sql"))
        .execute(dataSource);

    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("""
            SELECT state, verifiedAt, userVerification
            FROM identity_authenticationFlowMethod
            WHERE id = 1
            """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("state")).isEqualTo("PERMITTED");
      assertThat(result.getTimestamp("verifiedAt")).isNull();
      assertThat(result.getObject("userVerification")).isNull();
    }
  }

  /**
   * Comprova unicidade de provas ativas, checks fechados e cascata integral do fluxo.
   *
   * @throws SQLException quando o cenário de integridade não pode ser executado
   */
  @Test
  void constraints_shouldRejectDuplicateOrInvalidProofAndCascadeFlow() throws SQLException {
    long userId = insertUser();
    long flowId = insertAuthenticationFlow(userId);
    insertOpenProof(flowId, (byte) 1);

    try (Connection connection = dataSource.getConnection();
        PreparedStatement duplicate = connection.prepareStatement("""
            INSERT INTO identity_authenticationProof
              (idAuthenticationFlow, type, proofDigest, keyVersion, status, activeMarker,
               attemptCount, issuedAt, expiresAt)
            VALUES (?, 'EMAIL_OTP', ?, 'v1', 'OPEN', TRUE, 0,
                    CURRENT_TIMESTAMP(6), TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6)))
            """)) {
      duplicate.setLong(1, flowId);
      duplicate.setBytes(2, repeatedBytes((byte) 2, 32));
      assertThatThrownBy(duplicate::executeUpdate).isInstanceOf(SQLException.class);
    }

    try (Connection connection = dataSource.getConnection();
        PreparedStatement invalid = connection.prepareStatement("""
            INSERT INTO identity_authenticationProof
              (idAuthenticationFlow, type, proofDigest, status, activeMarker,
               attemptCount, issuedAt, expiresAt)
            VALUES (?, 'EMAIL_OTP', ?, 'UNKNOWN', NULL, 0,
                    CURRENT_TIMESTAMP(6), TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6)))
            """)) {
      invalid.setLong(1, flowId);
      invalid.setBytes(2, repeatedBytes((byte) 3, 32));
      assertThatThrownBy(invalid::executeUpdate).isInstanceOf(SQLException.class);
    }

    try (Connection connection = dataSource.getConnection();
        PreparedStatement delete = connection.prepareStatement("DELETE FROM identity_user WHERE id = ?")) {
      delete.setLong(1, userId);
      assertThat(delete.executeUpdate()).isOne();
    }

    assertThat(countRows("identity_authenticationFlow")).isZero();
    assertThat(countRows("identity_authenticationProof")).isZero();
  }

  /**
   * Comprova que a versão física permite somente um compare-and-set vencedor.
   *
   * @throws SQLException quando a janela concorrente não pode ser preparada
   */
  @Test
  void version_shouldAllowOnlyOneCompareAndSetWinner() throws SQLException {
    long windowId;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO security_authenticationWindow
              (identifierDigest, keyVersion, operation, windowStartedAt, windowEndsAt,
               failureCount, activeMarker)
            VALUES (?, 'v1', 'SIGN_IN', CURRENT_TIMESTAMP(6),
                    TIMESTAMPADD(MINUTE, 15, CURRENT_TIMESTAMP(6)), 0, TRUE)
            """, Statement.RETURN_GENERATED_KEYS)) {
      insert.setBytes(1, repeatedBytes((byte) 4, 32));
      assertThat(insert.executeUpdate()).isOne();
      try (ResultSet keys = insert.getGeneratedKeys()) {
        assertThat(keys.next()).isTrue();
        windowId = keys.getLong(1);
      }
    }

    assertThat(compareAndSetWindow(windowId, 0)).isOne();
    assertThat(compareAndSetWindow(windowId, 0)).isZero();
    assertThat(readLong("security_authenticationWindow", "version", windowId)).isOne();
    assertThat(readLong("security_authenticationWindow", "failureCount", windowId)).isOne();
  }

  /**
   * Confirma os tipos que os futuros mappings JPA devem reproduzir sem estratégia física implícita.
   *
   * @throws SQLException quando os metadados não podem ser consultados
   */
  @Test
  void metadata_shouldExposeJpaCompatibleNamesTypesAndVersions() throws SQLException {
    assertColumn("identity_authenticationFlow", "referenceHash", "binary(32)", "NO");
    assertColumn("identity_authenticationFlow", "idUser", "bigint", "YES");
    assertColumn("identity_authenticationFlowMethod", "state", "varchar(24)", "NO");
    assertColumn("identity_authenticationFlowMethod", "verifiedAt", "timestamp(6)", "YES");
    assertColumn("identity_authenticationProof", "proofDigest", "varbinary(96)", "NO");
    assertColumn("identity_totpFactor", "encryptedSecret", "varbinary(512)", "NO");
    assertColumn("identity_passkeyCredential", "credentialId", "varbinary(1024)", "NO");
    assertColumn("identity_passkeyCredential", "signatureCount", "bigint unsigned", "NO");
    assertColumn("identity_authSession", "originAddress", "varbinary(16)", "YES");

    for (String table : List.of(
        "identity_authenticationFlow",
        "identity_authenticationProof",
        "identity_totpFactor",
        "identity_emailFactor",
        "identity_recoveryCodeSet",
        "identity_passkeyCredential",
        "identity_authSession",
        "security_authenticationWindow")) {
      assertColumn(table, "version", "bigint", "NO");
    }
  }

  /**
   * Executa o catálogo consolidado de uma instalação global nova.
   *
   * @param target datasource selecionando o schema descartável
   */
  private void executeInit(DataSource target) {
    new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"),
        new ClassPathResource("db/global/init/02-seed.sql"),
        new ClassPathResource("db/global/init/03-procedures.sql"),
        new ClassPathResource("db/global/init/99-database-version.sql"))
        .execute(target);
  }

  /**
   * Executa em ordem todos os updates globais reais desde o marco inicial.
   *
   * @param target datasource selecionando o schema descartável
   */
  private void executeUpdates(DataSource target) {
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ByteArrayResource("""
        CREATE OR REPLACE
        SQL SECURITY INVOKER
        VIEW databaseVersion AS
        SELECT '00000000000' AS version;
        """.getBytes(StandardCharsets.UTF_8)));
    for (String script : List.of(
        "20260728_002_update.sql",
        "20260729_001_update.sql",
        "20260729_002_update.sql",
        "20260729_003_update.sql",
        "20260729_004_update.sql",
        "20260729_005_update.sql",
        "20260802_001_update.sql",
        "20260808_001_update.sql",
        "20260808_002_update.sql")) {
      populator.addScript(new ClassPathResource("db/global/update/" + script));
    }
    populator.execute(target);
  }

  /**
   * Lê o DDL normalizado pelo próprio MySQL para todas as tabelas da feature.
   *
   * @param target datasource selecionando o schema descartável
   * @return definições ordenadas por tabela
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private Map<String, String> readTableDefinitions(DataSource target) throws SQLException {
    Map<String, String> definitions = new LinkedHashMap<>();
    try (Connection connection = target.getConnection()) {
      for (String table : AUTHENTICATION_TABLES) {
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SHOW CREATE TABLE " + table)) {
          assertThat(result.next()).isTrue();
          definitions.put(table, result.getString(2));
        }
      }
    }
    return definitions;
  }

  /**
   * Insere a identidade mínima proprietária das estruturas do cenário.
   *
   * @return identificador gerado pelo MySQL
   * @throws SQLException quando a identidade não pode ser persistida
   */
  private long insertUser() throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO identity_user (email, normalizedEmail, status)
            VALUES ('schema@example.com', 'schema@example.com', 'ACTIVE')
            """, Statement.RETURN_GENERATED_KEYS)) {
      assertThat(insert.executeUpdate()).isOne();
      try (ResultSet keys = insert.getGeneratedKeys()) {
        assertThat(keys.next()).isTrue();
        return keys.getLong(1);
      }
    }
  }

  /**
   * Insere um fluxo aberto válido para a identidade informada.
   *
   * @param userId identificador da identidade proprietária
   * @return identificador gerado pelo MySQL
   * @throws SQLException quando o fluxo não pode ser persistido
   */
  private long insertAuthenticationFlow(long userId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO identity_authenticationFlow
              (idUser, referenceHash, purpose, primaryMethod, requiredAssurance,
               persistentLoginRequested, status, failureCount, issuedAt, expiresAt, correlationId)
            VALUES (?, ?, 'SIGN_IN', 'PASSWORD', 'MULTI_FACTOR', FALSE, 'OPEN', 0,
                    CURRENT_TIMESTAMP(6), TIMESTAMPADD(MINUTE, 15, CURRENT_TIMESTAMP(6)), ?)
            """, Statement.RETURN_GENERATED_KEYS)) {
      insert.setLong(1, userId);
      insert.setBytes(2, repeatedBytes((byte) 5, 32));
      insert.setBytes(3, repeatedBytes((byte) 6, 16));
      assertThat(insert.executeUpdate()).isOne();
      try (ResultSet keys = insert.getGeneratedKeys()) {
        assertThat(keys.next()).isTrue();
        return keys.getLong(1);
      }
    }
  }

  /**
   * Insere uma prova ativa cujo digest não carrega dado funcional.
   *
   * @param flowId identificador do fluxo proprietário
   * @param digestByte byte repetido no digest de teste
   * @throws SQLException quando a prova não pode ser persistida
   */
  private void insertOpenProof(long flowId, byte digestByte) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement insert = connection.prepareStatement("""
            INSERT INTO identity_authenticationProof
              (idAuthenticationFlow, type, proofDigest, keyVersion, status, activeMarker,
               attemptCount, issuedAt, expiresAt)
            VALUES (?, 'EMAIL_OTP', ?, 'v1', 'OPEN', TRUE, 0,
                    CURRENT_TIMESTAMP(6), TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP(6)))
            """)) {
      insert.setLong(1, flowId);
      insert.setBytes(2, repeatedBytes(digestByte, 32));
      assertThat(insert.executeUpdate()).isOne();
    }
  }

  /**
   * Tenta incrementar uma janela somente quando a versão esperada ainda é vigente.
   *
   * @param id identificador da janela
   * @param expectedVersion versão apresentada pelo concorrente
   * @return quantidade de linhas alteradas
   * @throws SQLException quando a tentativa falha
   */
  private int compareAndSetWindow(long id, long expectedVersion) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement update = connection.prepareStatement("""
            UPDATE security_authenticationWindow
            SET failureCount = failureCount + 1,
                version = version + 1
            WHERE id = ? AND version = ?
            """)) {
      update.setLong(1, id);
      update.setLong(2, expectedVersion);
      return update.executeUpdate();
    }
  }

  /**
   * Lê uma coluna numérica de uma linha preparada pelo próprio teste.
   *
   * @param table tabela física conhecida
   * @param column coluna física conhecida
   * @param id identificador da linha
   * @return valor persistido
   * @throws SQLException quando a consulta falha
   */
  private long readLong(String table, String column, long id) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT " + column + " FROM " + table + " WHERE id = ?")) {
      statement.setLong(1, id);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return result.getLong(1);
      }
    }
  }

  /**
   * Conta as linhas de uma tabela física conhecida da feature.
   *
   * @param table tabela física conhecida
   * @return quantidade de linhas
   * @throws SQLException quando a consulta falha
   */
  private long countRows(String table) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
      assertThat(result.next()).isTrue();
      return result.getLong(1);
    }
  }

  /**
   * Confirma tipo físico e nulidade de uma coluna planejada para mapping explícito.
   *
   * @param table tabela física esperada
   * @param column coluna física esperada
   * @param columnType tipo completo devolvido pelo MySQL
   * @param nullable marcador {@code YES} ou {@code NO}
   * @throws SQLException quando os metadados não podem ser consultados
   */
  private void assertColumn(String table, String column, String columnType, String nullable)
      throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement("""
            SELECT column_type, is_nullable
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND column_name = ?
            """)) {
      statement.setString(1, table);
      statement.setString(2, column);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("column_type")).isEqualTo(columnType);
        assertThat(result.getString("is_nullable")).isEqualTo(nullable);
        assertThat(result.next()).isFalse();
      }
    }
  }

  /**
   * Lê a versão única publicada pelo schema informado.
   *
   * @param target datasource selecionando o schema descartável
   * @return versão compacta
   * @throws SQLException quando a view não pode ser consultada
   */
  private String readVersion(DataSource target) throws SQLException {
    try (Connection connection = target.getConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT version FROM databaseVersion")) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  /**
   * Produz bytes determinísticos sem usar material de credencial real.
   *
   * @param value valor repetido
   * @param length tamanho desejado
   * @return novo vetor preenchido
   */
  private byte[] repeatedBytes(byte value, int length) {
    byte[] bytes = new byte[length];
    java.util.Arrays.fill(bytes, value);
    return bytes;
  }
}
