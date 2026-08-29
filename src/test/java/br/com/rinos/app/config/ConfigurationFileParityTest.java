package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Paridade dos arquivos de propriedades")
class ConfigurationFileParityTest {

  private static final Path MODEL_FILE = Path.of("application.properties.model");

  /**
   * Comprova que comentários, chaves e ordem permanecem espelhados no ambiente de desenvolvimento.
   *
   * @throws Exception quando um arquivo não pode ser lido
   */
  @Test
  void localFile_shouldMirrorModelStructure_whenLocalFileExists() throws Exception {
    Path local = Path.of("application.properties");
    Assumptions.assumeTrue(Files.exists(local),
        "O arquivo local é intencionalmente ausente em clones novos.");

    assertThat(structure(local)).isEqualTo(structure(MODEL_FILE));
  }

  /**
   * Comprova as definições persistentes que não podem depender de defaults do Hibernate.
   *
   * @throws Exception quando o modelo não pode ser lido
   */
  @Test
  void model_shouldDeclareStandardPhysicalNamesAndUtcPersistence() throws Exception {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(MODEL_FILE, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }

    assertThat(properties.getProperty("spring.jpa.hibernate.naming.physical-strategy"))
        .isEqualTo("org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
    assertThat(properties.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
        .isEqualTo("UTC");
    assertThat(properties.getProperty("spring.datasource.hikari.connection-init-sql"))
        .isEqualTo("SET time_zone = '+00:00'");
    assertThat(properties.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:mysql://localhost:3306/rinos_global?useUnicode=true&characterEncoding=UTF-8");
  }

  /**
   * Comprova que o bootstrap automático utiliza somente o catálogo global de updates.
   *
   * @throws Exception quando o modelo não pode ser lido
   */
  @Test
  void model_shouldEnableRfwUpdaterOnlyForGlobalCatalog_whenDatabaseBootstrapIsConfigured() throws Exception {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(MODEL_FILE, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }

    assertThat(properties.getProperty("rfw.database.update.enabled")).isEqualTo("true");
    assertThat(properties.getProperty("rfw.database.update.locations"))
        .isEqualTo("classpath:db/global/update/");
    assertThat(properties.getProperty("rfw.database.update.lock-timeout")).isEqualTo("30s");
  }

  /**
   * Comprova que o MySQL externo de testes exige habilitação explícita e URL sem database.
   *
   * @throws Exception quando o modelo não pode ser lido
   */
  @Test
  void model_shouldKeepExternalTestDatabaseDisabled_whenEnvironmentIsNotPrepared() throws Exception {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(MODEL_FILE, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }

    assertThat(properties.getProperty("rinos.test-database.external.enabled")).isEqualTo("false");
    assertThat(properties.getProperty("rinos.test-database.external.server-url"))
        .isEqualTo(
            "jdbc:mysql://localhost:3306/?useUnicode=true&characterEncoding=UTF-8");
    assertThat(properties.getProperty("rinos.test-database.external.username"))
        .isEqualTo("rinos_test");
  }

  /**
   * Comprova que a fila estrutural do storage é explicitamente configurada no modelo, sem depender de ambiente.
   *
   * @throws Exception quando o modelo não pode ser lido
   */
  @Test
  void model_shouldDeclareTenantStorageDefaults() throws Exception {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(MODEL_FILE, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }

    assertThat(properties.getProperty("rinos.storage.queue-poll-interval")).isEqualTo("30s");
    assertThat(properties.getProperty("rinos.storage.operation-lease")).isEqualTo("10m");
    assertThat(properties.getProperty("rinos.storage.operation-heartbeat-interval")).isEqualTo("30s");
    assertThat(properties.getProperty("rinos.storage.provisioning-maximum-attempts")).isEqualTo("3");
    assertThat(properties.getProperty("rinos.storage.maximum-concurrent-operations")).isEqualTo("1");
  }

  private static List<String> structure(Path file) throws Exception {
    return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
        .map(line -> {
          if (line.isBlank() || line.startsWith("#")) {
            return line;
          }
          int separator = line.indexOf('=');
          return separator < 0 ? line : line.substring(0, separator + 1);
        })
        .toList();
  }
}
