package br.com.rinos.app.api.dto;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta uma solicitação pública de nova comprovação sem carregar a prova anterior.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class RegistrationResendRequestDTO {

  private final String identifier;
  private final Locale locale;
  private final UUID correlationId;

  /**
   * Cria a solicitação com a correlação técnica da tentativa.
   *
   * @param identifier e-mail informado na etapa de ativação
   * @param locale idioma preferencial da mensagem
   * @param correlationId correlação aleatória sem dados pessoais
   */
  public RegistrationResendRequestDTO(
      String identifier,
      Locale locale,
      UUID correlationId) {
    this.identifier = identifier;
    this.locale = locale == null ? Locale.getDefault() : locale;
    this.correlationId = Objects.requireNonNull(
        correlationId,
        "correlationId must not be null");
  }

  /**
   * Retorna o identificador informado.
   *
   * @return e-mail ainda não normalizado
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Retorna o idioma efetivo.
   *
   * @return locale nunca nulo
   */
  public Locale getLocale() {
    return locale;
  }

  /**
   * Retorna a correlação técnica.
   *
   * @return UUID aleatório
   */
  public UUID getCorrelationId() {
    return correlationId;
  }

  /**
   * Evita expor o identificador em logs acidentais.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RegistrationResendRequestDTO[identifier=<redacted>, locale="
        + locale + ", correlationId=" + correlationId + "]";
  }
}
