package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Token criptográfico de comprovação")
class VerificationTokenServiceTest {

  private final VerificationTokenService service = new VerificationTokenService();

  /**
   * Gera valores URL-safe independentes com 256 bits antes da codificação.
   */
  @Test
  void generate_shouldReturnDistinctUrlSafeTokens_whenCalledRepeatedly() {
    String first = service.generate();
    String second = service.generate();

    assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
    assertThat(second).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(first);
  }

  /**
   * Produz SHA-256 e aceita somente a prova original.
   */
  @Test
  void matches_shouldAcceptOnlyOriginalToken_whenHashWasPersisted() {
    String token = service.generate();
    byte[] hash = service.hash(token);

    assertThat(hash).hasSize(32);
    assertThat(service.matches(token, hash)).isTrue();
    assertThat(service.matches(service.generate(), hash)).isFalse();
  }
}
