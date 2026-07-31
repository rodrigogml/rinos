package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;

/**
 * Evidência reproduzível da calibração sem senha ou hash.
 *
 * @param measuredAt instante UTC
 * @param javaVersion JVM utilizada
 * @param operatingSystem sistema e arquitetura
 * @param availableProcessors processadores visíveis
 * @param memoryKib memória Argon2id
 * @param iterations iterações Argon2id
 * @param parallelism paralelismo Argon2id
 * @param saltLength bytes do salt
 * @param hashLength bytes do hash
 * @param measurements quantidade de medições
 * @param medianMillis mediana em milissegundos
 * @param percentile95Millis percentil 95 em milissegundos
 * @param accepted atende ao intervalo e limite definidos
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record PasswordHashCalibrationReportVO(
    Instant measuredAt,
    String javaVersion,
    String operatingSystem,
    int availableProcessors,
    int memoryKib,
    int iterations,
    int parallelism,
    int saltLength,
    int hashLength,
    int measurements,
    double medianMillis,
    double percentile95Millis,
    boolean accepted) {
}
