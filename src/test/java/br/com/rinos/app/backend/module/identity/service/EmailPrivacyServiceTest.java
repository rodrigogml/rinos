package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Minimização pública de e-mail")
class EmailPrivacyServiceTest {

  private EmailPrivacyService service;

  @BeforeEach
  void setUp() {
    service = new EmailPrivacyService();
  }

  /**
   * Preserva somente o primeiro caractere do local quando ele ajuda a reconhecer o destino.
   */
  @Test
  void maskForPublicDisplay_shouldExposeOnlyFirstLocalCharacter_whenLocalIsLongerThanTwo() {
    assertThat(service.maskForPublicDisplay("person@example.com"))
        .isEqualTo("p***@example.com");
  }

  /**
   * Trata o primeiro caractere Unicode como unidade, sem produzir uma sequência inválida.
   */
  @Test
  void maskForPublicDisplay_shouldPreserveFirstUnicodeCodePoint_whenLocalStartsWithUnicode() {
    assertThat(service.maskForPublicDisplay("ábc@example.com"))
        .isEqualTo("á***@example.com");
  }

  /**
   * Não revela qualquer caractere de locais curtos, pois um caractere os identificaria demais.
   */
  @Test
  void maskForPublicDisplay_shouldHideEntireLocalPart_whenLocalHasAtMostTwoCharacters() {
    assertThat(service.maskForPublicDisplay("a@example.com"))
        .isEqualTo("***@example.com");
    assertThat(service.maskForPublicDisplay("ab@example.com"))
        .isEqualTo("***@example.com");
  }

  /**
   * Recusa valor ausente em vez de permitir que uma superfície pública o exiba por engano.
   */
  @Test
  void maskForPublicDisplay_shouldRejectNullEmail() {
    assertThatThrownBy(() -> service.maskForPublicDisplay(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("email must not be null");
  }

  /**
   * Recusa formatos sem uma única separação inequívoca entre local e domínio.
   */
  @Test
  void maskForPublicDisplay_shouldRejectMalformedEmail() {
    assertThatThrownBy(() -> service.maskForPublicDisplay("invalid@@example.com"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("email must contain one local and one domain");
  }
}
