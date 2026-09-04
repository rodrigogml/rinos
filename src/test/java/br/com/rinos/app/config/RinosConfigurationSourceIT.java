package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import br.com.rinos.app.testsupport.mysql.MySqlTestDatabase;

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

  /**
   * Comprova que o JAR real serve a rota pública com origem canônica, porta interna e proxy
   * confiável definidos exclusivamente no arquivo.
   *
   * @throws Exception quando o processo, o banco descartável ou a requisição falhar
   */
  @Test
  void application_shouldServeLogin_withExplicitReverseProxyConfiguration() throws Exception {
    MySqlTestDatabase testDatabase = MySqlTestDatabase.openIfAvailable().orElse(null);
    Assumptions.assumeTrue(
        testDatabase != null,
        "Configure o MySQL externo de testes ou disponibilize Docker para executar este gate.");
    try (testDatabase) {
      DataSource dataSource = testDatabase.recreateSchema();
      initializeGlobalDatabase(dataSource);
      DriverManagerDataSource driverDataSource = (DriverManagerDataSource) dataSource;
      int port = availablePort();
      Files.writeString(
          temporaryDirectory.resolve("application.properties"),
          reverseProxyProperties(driverDataSource, port),
          StandardCharsets.UTF_8);
      Path output = temporaryDirectory.resolve("reverse-proxy-smoke.log");
      Process process = startApplication(List.of(), false, output);
      try {
        boolean started = awaitStartup(process, output, Duration.ofSeconds(40));
        String startupOutput = Files.exists(output)
            ? Files.readString(output, StandardCharsets.UTF_8)
            : "startup log was not created";
        assertThat(started).as(startupOutput).isTrue();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/login"))
            .timeout(Duration.ofSeconds(15))
            .header("Forwarded", "for=203.0.113.10;proto=https;host=attacker.invalid")
            .header("X-Forwarded-For", "203.0.113.10")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "attacker.invalid")
            .GET()
            .build();
        HttpResponse<String> response = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isBetween(200, 399);
        assertThat(response.body())
            .contains("id=\"outlet\"", "/VAADIN/build/")
            .doesNotContain("attacker.invalid");
        assertThat(response.headers().allValues("Location"))
            .noneMatch(location -> location.contains("attacker.invalid"));
        assertThat(startupOutput)
            .contains("Started RinosApplication")
            .doesNotContain("attacker.invalid");
      } finally {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor(5, TimeUnit.SECONDS);
        }
      }
    }
  }

  private ProcessResult runApplication(List<String> additionalArguments,
      boolean addMaliciousEnvironment) throws Exception {
    Path output = temporaryDirectory.resolve("process-output-" + System.nanoTime() + ".log");
    Process process = startApplication(additionalArguments, addMaliciousEnvironment, output);
    boolean finished = process.waitFor(40, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
    }
    int exitCode = finished ? process.exitValue() : -1;
    String processOutput = Files.readString(output, StandardCharsets.UTF_8);
    return new ProcessResult(finished, exitCode, processOutput);
  }

  private Process startApplication(
      List<String> additionalArguments,
      boolean addMaliciousEnvironment,
      Path output) throws Exception {
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
    return builder.start();
  }

  private static boolean awaitStartup(
      Process process,
      Path output,
      Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (process.isAlive() && System.nanoTime() < deadline) {
      String content = Files.exists(output)
          ? Files.readString(output, StandardCharsets.UTF_8)
          : "";
      if (content.contains("Started RinosApplication")) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  private static int availablePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void initializeGlobalDatabase(DataSource dataSource) {
    new ResourceDatabasePopulator(
        new ClassPathResource("db/global/init/01-ddl.sql"),
        new ClassPathResource("db/global/init/02-seed.sql"),
        new ClassPathResource("db/global/init/03-procedures.sql"),
        new ClassPathResource("db/global/init/99-database-version.sql"))
        .execute(dataSource);
  }

  private static String reverseProxyProperties(
      DriverManagerDataSource dataSource,
      int port) {
    return """
        spring.application.name=Rinos
        server.port=%d
        server.forward-headers-strategy=none
        rinos.application.public-base-url=https://app.rinos.com.br
        rinos.proxy.trusted-proxies=127.0.0.1
        rinos.maintenance.instance-id=proxy-smoke
        spring.datasource.url=%s
        spring.datasource.username=%s
        spring.datasource.password=%s
        spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00'
        spring.jpa.hibernate.ddl-auto=none
        spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
        spring.jpa.properties.hibernate.jdbc.time_zone=UTC
        spring.mail.host=localhost
        spring.mail.port=1025
        rfw.mail.default-from-address=no-reply@localhost
        rfw.database.update.enabled=false
        """.formatted(
        port,
        dataSource.getUrl(),
        dataSource.getUsername(),
        dataSource.getPassword());
  }

  private static Path javaExecutable() {
    String executable = System.getProperty("os.name").toLowerCase().contains("win")
        ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", executable);
  }

  private static String validProperties() {
    return """
        spring.main.web-application-type=none
        spring.main.lazy-initialization=true
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
