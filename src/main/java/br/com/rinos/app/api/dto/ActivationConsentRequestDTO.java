package br.com.rinos.app.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Transporta uma continuação de ativação e os documentos aceitos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public class ActivationConsentRequestDTO {

  private final String activationReference;
  private final List<String> acceptedLegalDocumentIds;
  private final UUID correlationId;

  /**
   * Cria uma fotografia defensiva da solicitação.
   *
   * @param activationReference prova opaca originalmente recebida
   * @param acceptedLegalDocumentIds versões aceitas
   * @param correlationId correlação técnica
   */
  public ActivationConsentRequestDTO(
      String activationReference,
      List<String> acceptedLegalDocumentIds,
      UUID correlationId) {
    this.activationReference = activationReference;
    this.acceptedLegalDocumentIds = acceptedLegalDocumentIds == null
        ? List.of()
        : List.copyOf(acceptedLegalDocumentIds);
    this.correlationId = correlationId;
  }

  /**
   * Retorna a referência opaca.
   *
   * @return prova da ativação
   */
  public String getActivationReference() {
    return activationReference;
  }

  /**
   * Retorna as versões aceitas.
   *
   * @return lista imutável
   */
  public List<String> getAcceptedLegalDocumentIds() {
    return acceptedLegalDocumentIds;
  }

  /**
   * Retorna a correlação técnica.
   *
   * @return UUID da tentativa
   */
  public UUID getCorrelationId() {
    return correlationId;
  }

  /**
   * Evita expor a referência de uso único em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "ActivationConsentRequestDTO[activationReference=REDACTED, "
        + "acceptedLegalDocumentCount=" + acceptedLegalDocumentIds.size()
        + ", correlationId=" + correlationId + "]";
  }
}
