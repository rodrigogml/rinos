package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Integridade do documento legal")
class LegalDocumentIntegrityServiceTest {

  private final LegalDocumentIntegrityService service = new LegalDocumentIntegrityService();

  /**
   * Detecta qualquer alteração no conteúdo apresentado.
   */
  @Test
  void matches_shouldAcceptOnlyOriginalContent_whenHashWasPersisted() {
    byte[] hash = service.hash("Conteúdo legal versão 1");

    assertThat(hash).hasSize(32);
    assertThat(service.matches("Conteúdo legal versão 1", hash)).isTrue();
    assertThat(service.matches("Conteúdo legal versão 2", hash)).isFalse();
  }
}
