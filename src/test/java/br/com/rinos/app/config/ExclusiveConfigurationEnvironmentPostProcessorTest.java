package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

@DisplayName("Origem exclusiva do application.properties")
class ExclusiveConfigurationEnvironmentPostProcessorTest {

  @TempDir
  Path temporaryDirectory;

  /**
   * Comprova que todas as fontes preexistentes são substituídas pelo bootstrap fixo.
   *
   * @throws Exception quando o arquivo temporário não pode ser criado
   */
  @Test
  void postProcessEnvironment_shouldRemoveExternalSources_whenFileIsValid() throws Exception {
    Path configuration = temporaryDirectory.resolve("application.properties");
    Files.writeString(configuration, "rinos.maintenance.instance-id=file-instance\n",
        StandardCharsets.UTF_8);
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(
        new MapPropertySource("commandLineArgs",
            Map.of("rinos.maintenance.instance-id", "command-line-instance")));
    ExclusiveConfigurationEnvironmentPostProcessor processor =
        new ExclusiveConfigurationEnvironmentPostProcessor(configuration);

    processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

    assertThat(environment.getPropertySources().stream().map(source -> source.getName()))
        .containsExactly(ExclusiveConfigurationEnvironmentPostProcessor.BOOTSTRAP_PROPERTY_SOURCE);
    assertThat(environment.getProperty("spring.config.location"))
        .isEqualTo(configuration.toAbsolutePath().normalize().toUri().toString());
    assertThat(environment.getProperty("rinos.maintenance.instance-id")).isNull();
  }

  /**
   * Comprova que a inicialização falha sem o arquivo explícito.
   */
  @Test
  void postProcessEnvironment_shouldFail_whenFileDoesNotExist() {
    Path missing = temporaryDirectory.resolve("missing.properties");
    ExclusiveConfigurationEnvironmentPostProcessor processor =
        new ExclusiveConfigurationEnvironmentPostProcessor(missing);

    assertThatThrownBy(() -> processor.postProcessEnvironment(
        new StandardEnvironment(), new SpringApplication(Object.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Arquivo obrigatório não encontrado");
  }

  /**
   * Comprova que interpolação de variável externa é recusada.
   *
   * @throws Exception quando o arquivo temporário não pode ser criado
   */
  @Test
  void postProcessEnvironment_shouldFail_whenFileContainsPlaceholder() throws Exception {
    Path configuration = temporaryDirectory.resolve("application.properties");
    Files.writeString(configuration, "rinos.secret=${RINOS_SECRET}\n", StandardCharsets.UTF_8);
    ExclusiveConfigurationEnvironmentPostProcessor processor =
        new ExclusiveConfigurationEnvironmentPostProcessor(configuration);

    assertThatThrownBy(() -> processor.postProcessEnvironment(
        new StandardEnvironment(), new SpringApplication(Object.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("não pode interpolar");
  }

  /**
   * Comprova que profiles e imports não conseguem acrescentar arquivos.
   *
   * @throws Exception quando o arquivo temporário não pode ser criado
   */
  @Test
  void postProcessEnvironment_shouldFail_whenFileImportsAnotherSource() throws Exception {
    Path configuration = temporaryDirectory.resolve("application.properties");
    Files.writeString(configuration, "spring.config.import=file:other.properties\n",
        StandardCharsets.UTF_8);
    ExclusiveConfigurationEnvironmentPostProcessor processor =
        new ExclusiveConfigurationEnvironmentPostProcessor(configuration);

    assertThatThrownBy(() -> processor.postProcessEnvironment(
        new StandardEnvironment(), new SpringApplication(Object.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("chave de bootstrap proibida");
  }
}
