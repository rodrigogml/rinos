package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Transporta uma prova opaca para ativação local.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public class RegistrationActivationRequestDTO {

  private final String identifier;
  private final String proof;
  private final UUID correlationId;

  /**
   * Cria a solicitação imutável na borda pública.
   *
   * @param identifier e-mail conhecido, opcional para deep links
   * @param proof prova opaca
   * @param correlationId correlação técnica
   */
  public RegistrationActivationRequestDTO(
      String identifier,
      String proof,
      UUID correlationId) {
    this.identifier = identifier;
    this.proof = proof;
    this.correlationId = correlationId;
  }

  /**
   * Retorna o identificador auxiliar.
   *
   * @return e-mail informado ou {@code null}
   */
  public String getIdentifier() {
    return identifier;
  }

  /**
   * Retorna a prova opaca.
   *
   * @return prova apresentada
   */
  public String getProof() {
    return proof;
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
   * Evita expor identificador e prova em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RegistrationActivationRequestDTO[identifier=REDACTED, proof=REDACTED, "
        + "correlationId=" + correlationId + "]";
  }
}
