package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;

@DisplayName("Normalização do e-mail global")
class EmailNormalizationServiceTest {

  private final EmailNormalizationService service = new EmailNormalizationService();

  /**
   * Preserva a forma de apresentação sem espaços externos e normaliza com locale neutro.
   */
  @Test
  void normalize_shouldTrimAndLowercase_whenEmailIsValid() {
    NormalizedEmailVO result = service.normalize("  User.I@Example.COM  ");

    assertThat(result.email()).isEqualTo("User.I@Example.COM");
    assertThat(result.normalizedEmail()).isEqualTo("user.i@example.com");
  }

  /**
   * Comprova que o resultado não depende do locale turco da máquina.
   */
  @Test
  void normalize_shouldUseRootLocale_whenDefaultLocaleHasSpecialCasing() {
    NormalizedEmailVO result = service.normalize("IDENTITY@EXAMPLE.COM");

    assertThat(result.normalizedEmail()).isEqualTo("identity@example.com");
  }

  /**
   * Rejeita ausência de valor antes de acessar qualquer persistência.
   */
  @Test
  void normalize_shouldRejectNull_whenEmailIsAbsent() {
    assertThatThrownBy(() -> service.normalize(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("email must not be null");
  }

  /**
   * Rejeita valores compostos apenas por espaços.
   */
  @Test
  void normalize_shouldRejectBlank_whenEmailHasNoContent() {
    assertThatThrownBy(() -> service.normalize("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must not be blank");
  }

  /**
   * Protege o limite físico da coluna antes da persistência.
   */
  @Test
  void normalize_shouldRejectOversizedValue_whenEmailExceedsColumnLength() {
    assertThatThrownBy(() -> service.normalize("a".repeat(321)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must not exceed 320 characters");
  }

  /**
   * Rejeita valores sem mailbox e domínio estruturalmente válidos.
   */
  @Test
  void normalize_shouldRejectMalformedAddress_whenStructureIsInvalid() {
    assertThatThrownBy(() -> service.normalize("not-an-email"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must be structurally valid");
    assertThatThrownBy(() -> service.normalize("user@@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must be structurally valid");
    assertThatThrownBy(() -> service.normalize("User Name <user@example.com>"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must be structurally valid");
  }
}
