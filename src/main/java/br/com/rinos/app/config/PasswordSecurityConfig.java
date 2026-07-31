package br.com.rinos.app.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Publica o encoder Argon2id do Rinos com identificador e parâmetros persistidos no hash.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Configuration(proxyBeanMethods = false)
public class PasswordSecurityConfig {

  /** Identificador estável gravado antes de cada hash Argon2id. */
  public static final String ARGON2ID_ENCODER_ID = "argon2id";

  /**
   * Cria o encoder compartilhado com a RFW sem reduzir o piso validado pelas properties.
   *
   * @param properties parâmetros exclusivos do {@code application.properties}
   * @return encoder delegante capaz de validar parâmetros históricos contidos no hash
   */
  @Bean
  public PasswordEncoder rinosPasswordEncoder(PasswordHashPropertiesConfig properties) {
    PasswordEncoder argon2id = createArgon2idEncoder(properties);
    return new DelegatingPasswordEncoder(
        ARGON2ID_ENCODER_ID,
        Map.of(ARGON2ID_ENCODER_ID, argon2id));
  }

  /**
   * Cria a implementação Argon2id para uso operacional e calibração.
   *
   * @param properties parâmetros validados
   * @return encoder Argon2id
   */
  public static PasswordEncoder createArgon2idEncoder(
      PasswordHashPropertiesConfig properties) {
    return new Argon2PasswordEncoder(
        properties.saltLength(),
        properties.hashLength(),
        properties.parallelism(),
        properties.memoryKib(),
        properties.iterations());
  }
}
