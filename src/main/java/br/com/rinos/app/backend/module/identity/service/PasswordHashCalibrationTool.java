package br.com.rinos.app.backend.module.identity.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.rinos.app.backend.module.identity.vo.PasswordHashCalibrationReportVO;
import br.com.rinos.app.config.PasswordHashPropertiesConfig;
import br.com.rinos.app.config.PasswordSecurityConfig;

/**
 * Ferramenta CLI que calibra os parâmetros escritos explicitamente em {@code application.properties}.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class PasswordHashCalibrationTool {

  private PasswordHashCalibrationTool() {
  }

  /**
   * Executa dez aquecimentos e 50 medições no servidor-alvo.
   *
   * @param arguments sem argumentos; o arquivo é lido da raiz corrente
   * @throws IOException quando {@code application.properties} não pode ser lido
   */
  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 0) {
      throw new IllegalArgumentException("the calibration tool does not accept arguments");
    }
    PasswordHashPropertiesConfig properties = loadProperties(
        Path.of("application.properties"));
    PasswordEncoder encoder = PasswordSecurityConfig.createArgon2idEncoder(properties);
    PasswordHashCalibrationReportVO report = new PasswordHashCalibrationService().calibrate(
        encoder,
        properties,
        PasswordHashCalibrationService.DEFAULT_WARMUP_OPERATIONS,
        PasswordHashCalibrationService.MINIMUM_MEASUREMENTS);
    System.out.printf(Locale.ROOT,
        "measuredAt=%s%njava=%s%nos=%s%nprocessors=%d%n"
            + "memoryKib=%d%niterations=%d%nparallelism=%d%nsaltLength=%d%nhashLength=%d%n"
            + "measurements=%d%nmedianMillis=%.3f%np95Millis=%.3f%nresult=%s%n",
        report.measuredAt(),
        report.javaVersion(),
        report.operatingSystem(),
        report.availableProcessors(),
        report.memoryKib(),
        report.iterations(),
        report.parallelism(),
        report.saltLength(),
        report.hashLength(),
        report.measurements(),
        report.medianMillis(),
        report.percentile95Millis(),
        report.accepted() ? "PASS" : "FAIL");
  }

  private static PasswordHashPropertiesConfig loadProperties(Path path) throws IOException {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(path)) {
      properties.load(input);
    }
    return new PasswordHashPropertiesConfig(
        integer(properties, "rinos.password-hash.memory-kib", 19_456),
        integer(properties, "rinos.password-hash.iterations", 2),
        integer(properties, "rinos.password-hash.parallelism", 1),
        integer(properties, "rinos.password-hash.salt-length", 16),
        integer(properties, "rinos.password-hash.hash-length", 32));
  }

  private static int integer(Properties properties, String key, int fallback) {
    return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).strip());
  }
}
