package br.com.rinos.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("Encoder Argon2id do Rinos")
class PasswordSecurityConfigTest {

  @Test
  void encode_shouldPersistIdentifierAndParameters_andVerifyPassword() {
    PasswordEncoder encoder = new PasswordSecurityConfig().rinosPasswordEncoder(properties());

    String hash = encoder.encode("Unique-Password-7!");

    assertThat(hash).startsWith("{argon2id}$argon2id$");
    assertThat(hash).contains("m=19456,t=2,p=1");
    assertThat(encoder.matches("Unique-Password-7!", hash)).isTrue();
    assertThat(encoder.matches("Wrong-Password-7!", hash)).isFalse();
  }

  @Test
  void matches_shouldVerifyHistoricalArgonParametersEncodedInHash() {
    PasswordEncoder current = new PasswordSecurityConfig().rinosPasswordEncoder(properties());
    PasswordEncoder historical = new Argon2PasswordEncoder(16, 32, 1, 19_456, 3);
    String historicalHash = "{argon2id}" + historical.encode("Unique-Password-7!");

    assertThat(current.matches("Unique-Password-7!", historicalHash)).isTrue();
  }

  private static PasswordHashPropertiesConfig properties() {
    return new PasswordHashPropertiesConfig(19_456, 2, 1, 16, 32);
  }
}
