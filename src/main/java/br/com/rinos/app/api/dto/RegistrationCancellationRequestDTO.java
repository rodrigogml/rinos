package br.com.rinos.app.api.dto;

import java.util.Locale;
import java.util.UUID;

/**
 * Solicita de forma neutra uma prova de cancelamento de cadastro pendente.
 *
 * @param identifier e-mail informado pela pessoa
 * @param locale idioma preferencial da mensagem
 * @param correlationId correlação técnica sem dados pessoais
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCancellationRequestDTO(
    String identifier,
    Locale locale,
    UUID correlationId) {

  /**
   * Evita expor o identificador em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RegistrationCancellationRequestDTO[identifier=REDACTED, locale="
        + locale + ", correlationId=" + correlationId + "]";
  }
}
