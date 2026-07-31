package br.com.rinos.app.backend.module.identity.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.rinos.app.backend.module.identity.vo.PasswordHashCalibrationReportVO;
import br.com.rinos.app.config.PasswordHashPropertiesConfig;

/**
 * Mede o custo do Argon2id no perfil real do servidor sem expor entradas ou saídas.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public class PasswordHashCalibrationService {

  /** Quantidade mínima contratual de medições. */
  public static final int MINIMUM_MEASUREMENTS = 50;
  /** Aquecimento padrão antes da amostra. */
  public static final int DEFAULT_WARMUP_OPERATIONS = 10;

  private static final double MINIMUM_MEDIAN_MILLIS = 500.0;
  private static final double MAXIMUM_MEDIAN_MILLIS = 1_000.0;
  private static final double MAXIMUM_PERCENTILE_95_MILLIS = 1_500.0;

  /**
   * Executa aquecimento e amostra mínima, descartando cada hash produzido.
   *
   * @param encoder encoder Argon2id sem prefixo delegante
   * @param properties parâmetros medidos
   * @param warmupOperations quantidade positiva de aquecimentos
   * @param measurements ao menos 50 medições
   * @return relatório operacional sem segredo
   */
  public PasswordHashCalibrationReportVO calibrate(
      PasswordEncoder encoder,
      PasswordHashPropertiesConfig properties,
      int warmupOperations,
      int measurements) {
    Objects.requireNonNull(encoder, "encoder must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    if (warmupOperations < 1) {
      throw new IllegalArgumentException("warmupOperations must be positive");
    }
    if (measurements < MINIMUM_MEASUREMENTS) {
      throw new IllegalArgumentException("measurements must be at least 50");
    }
    char[] sample = "Calibration-Only-7!Rinos".toCharArray();
    try {
      for (int index = 0; index < warmupOperations; index++) {
        encoder.encode(java.nio.CharBuffer.wrap(sample));
      }
      long[] elapsedNanos = new long[measurements];
      for (int index = 0; index < measurements; index++) {
        long startedAt = System.nanoTime();
        encoder.encode(java.nio.CharBuffer.wrap(sample));
        elapsedNanos[index] = System.nanoTime() - startedAt;
      }
      Arrays.sort(elapsedNanos);
      double median = median(elapsedNanos) / 1_000_000.0;
      double percentile95 = percentile95(elapsedNanos) / 1_000_000.0;
      boolean accepted = median >= MINIMUM_MEDIAN_MILLIS
          && median <= MAXIMUM_MEDIAN_MILLIS
          && percentile95 <= MAXIMUM_PERCENTILE_95_MILLIS;
      Runtime runtime = Runtime.getRuntime();
      return new PasswordHashCalibrationReportVO(
          Instant.now(),
          System.getProperty("java.version"),
          System.getProperty("os.name") + " " + System.getProperty("os.arch"),
          runtime.availableProcessors(),
          properties.memoryKib(),
          properties.iterations(),
          properties.parallelism(),
          properties.saltLength(),
          properties.hashLength(),
          measurements,
          median,
          percentile95,
          accepted);
    } finally {
      Arrays.fill(sample, '\0');
    }
  }

  private static double median(long[] sortedValues) {
    int middle = sortedValues.length / 2;
    if (sortedValues.length % 2 == 1) {
      return sortedValues[middle];
    }
    return (sortedValues[middle - 1] + sortedValues[middle]) / 2.0;
  }

  private static long percentile95(long[] sortedValues) {
    int index = (int) Math.ceil(sortedValues.length * 0.95) - 1;
    return sortedValues[Math.max(0, index)];
  }
}
