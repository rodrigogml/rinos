package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Inicialização real com origem exclusiva")
class RinosConfigurationSourceIT {

  @TempDir
  Path temporaryDirectory;

  /**
   * Comprova pelo JAR real que CLI, propriedade JVM e ambiente não sobrescrevem o arquivo.
   *
   * @throws Exception quando o processo controlado não pode ser executado
   */
  @Test
  void application_shouldUseOnlyRootFile_whenExternalSourcesTryToOverrideIt() throws Exception {
    Files.writeString(temporaryDirectory.resolve("application.properties"),
        validProperties(), StandardCharsets.UTF_8);
    Files.writeString(temporaryDirectory.resolve("malicious.properties"),
        "rinos.maintenance.instance-id=\n", StandardCharsets.UTF_8);

    ProcessResult result = runApplication(List.of(
        "-Drinos.maintenance.instance-id=",
        "-Dspring.config.location=file:malicious.properties",
        "--rinos.maintenance.instance-id=",
        "--spring.config.location=file:malicious.properties"), true);

    assertThat(result.finished()).isTrue();
    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("Started RinosApplication");
  }

  /**
   * Comprova que o JAR real não inicia sem o arquivo obrigatório.
   *
   * @throws Exception quando o processo controlado não pode ser executado
   */
  @Test
  void application_shouldFail_whenRootFileIsMissing() throws Exception {
    ProcessResult result = runApplication(List.of(), false);

    assertThat(result.finished()).isTrue();
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("Arquivo obrigatório não encontrado");
  }

  /**
   * Comprova que a migration global impede o startup quando o banco configurado não está disponível.
   *
   * @throws Exception quando o processo controlado não pode ser executado
   */
  @Test
  void application_shouldFailWithSafeDiagnostic_whenGlobalDataSourceIsMissing() throws Exception {
    String properties = validProperties().replace(
        "rfw.database.update.enabled=false",
        "rfw.database.update.enabled=true");
    Files.writeString(temporaryDirectory.resolve("application.properties"),
        properties, StandardCharsets.UTF_8);

    ProcessResult result = runApplication(List.of(), false);

    assertThat(result.finished()).isTrue();
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("[CONFIGURATION]", "nenhum DataSource foi encontrado");
    assertThat(result.output()).doesNotContain(
        "Started RinosApplication",
        "jdbc:mysql:",
        "spring.datasource.password");
  }

  /**
   * Comprova que o JAR real falha quando uma integração habilitada está incompleta.
   *
   * @throws Exception quando o processo controlado não pode ser executado
   */
  @Test
  void application_shouldFail_whenMailPortIsInvalid() throws Exception {
    Files.writeString(temporaryDirectory.resolve("application.properties"),
        validProperties() + "spring.mail.port=0\n",
        StandardCharsets.UTF_8);

    ProcessResult result = runApplication(List.of(), false);

    assertThat(result.finished()).isTrue();
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).contains("spring.mail.port deve estar entre 1 e 65535");
  }

  private ProcessResult runApplication(List<String> additionalArguments,
      boolean addMaliciousEnvironment) throws Exception {
    Path output = temporaryDirectory.resolve("process-output-" + System.nanoTime() + ".log");
    List<String> command = new ArrayList<>();
    command.add(javaExecutable().toString());
    for (String argument : additionalArguments) {
      if (argument.startsWith("-D")) {
        command.add(argument);
      }
    }
    command.add("-jar");
    command.add(Path.of("target", "rinos-1.0.0.jar").toAbsolutePath().toString());
    for (String argument : additionalArguments) {
      if (!argument.startsWith("-D")) {
        command.add(argument);
      }
    }
    ProcessBuilder builder = new ProcessBuilder(command)
        .directory(temporaryDirectory.toFile())
        .redirectErrorStream(true)
        .redirectOutput(output.toFile());
    if (addMaliciousEnvironment) {
      builder.environment().put("RINOS_MAINTENANCE_INSTANCE_ID", "");
      builder.environment().put("SPRING_CONFIG_LOCATION", "file:malicious.properties");
    }
    Process process = builder.start();
    boolean finished = process.waitFor(40, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
    }
    int exitCode = finished ? process.exitValue() : -1;
    String processOutput = Files.readString(output, StandardCharsets.UTF_8);
    return new ProcessResult(finished, exitCode, processOutput);
  }

  private static Path javaExecutable() {
    String executable = System.getProperty("os.name").toLowerCase().contains("win")
        ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", executable);
  }

  private static String validProperties() {
    return """
        spring.main.web-application-type=none
        spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,com.vaadin.flow.spring.SpringBootAutoConfiguration,com.vaadin.flow.spring.SpringSecurityAutoConfiguration
        rinos.maintenance.instance-id=file-instance
        spring.mail.host=localhost
        spring.mail.port=1025
        rfw.mail.default-from-address=no-reply@localhost
        rfw.database.update.enabled=false
        """;
  }

  private record ProcessResult(boolean finished, int exitCode, String output) {
  }
}
