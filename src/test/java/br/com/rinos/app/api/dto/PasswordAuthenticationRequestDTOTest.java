package br.com.rinos.app.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Pedido público de autenticação por senha")
class PasswordAuthenticationRequestDTOTest {

  @Test
  void consumePassword_shouldTransferOnceAndRedactEverySensitiveField() {
    PasswordAuthenticationRequestDTO request = new PasswordAuthenticationRequestDTO(
        "person@example.test",
        "Password1!".toCharArray(),
        true,
        "turnstile-secret",
        "198.51.100.12",
        UUID.fromString("d9b36467-3bee-43d6-9619-86728ca5863a"));

    assertThat(request.consumePassword()).containsExactly("Password1!".toCharArray());
    assertThat(request.consumePassword()).containsOnly('\0');
    assertThat(request.toString())
        .doesNotContain("person@example.test", "Password1!", "turnstile-secret", "198.51.100.12");
  }
}
