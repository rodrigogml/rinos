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
