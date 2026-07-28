package br.com.rinos.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Define os parâmetros Argon2id calibráveis sem permitir redução do piso de segurança.
 *
 * @param memoryKib memória em KiB
 * @param iterations quantidade de iterações
 * @param parallelism paralelismo
 * @param saltLength tamanho do salt em bytes
 * @param hashLength tamanho do hash em bytes
 * @author Rodrigo Leitão
 * @since 2026-07-27
 */
@ConfigurationProperties("rinos.password-hash")
public record PasswordHashPropertiesConfig(
    @DefaultValue("19456") int memoryKib,
    @DefaultValue("2") int iterations,
    @DefaultValue("1") int parallelism,
    @DefaultValue("16") int saltLength,
    @DefaultValue("32") int hashLength) {

  /** Piso mínimo de memória aprovado para Argon2id. */
  public static final int MINIMUM_MEMORY_KIB = 19_456;
  /** Piso mínimo de iterações aprovado para Argon2id. */
  public static final int MINIMUM_ITERATIONS = 2;
  /** Piso mínimo do salt em bytes. */
  public static final int MINIMUM_SALT_LENGTH = 16;
  /** Piso mínimo do hash em bytes. */
  public static final int MINIMUM_HASH_LENGTH = 32;

  /**
   * Impede que uma configuração externa reduza o piso aprovado.
   */
  public PasswordHashPropertiesConfig {
    if (memoryKib < MINIMUM_MEMORY_KIB || iterations < MINIMUM_ITERATIONS
        || parallelism < 1 || saltLength < MINIMUM_SALT_LENGTH
        || hashLength < MINIMUM_HASH_LENGTH) {
      throw new IllegalArgumentException(
          "Os parâmetros Argon2id não podem ficar abaixo do piso de segurança.");
    }
  }
}
