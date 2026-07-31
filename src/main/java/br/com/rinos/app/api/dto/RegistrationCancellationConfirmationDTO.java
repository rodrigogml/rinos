package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Confirma o cancelamento mediante o identificador e a prova recebida por e-mail.
 *
 * @param identifier e-mail informado pela pessoa
 * @param proof prova opaca de uso único
 * @param correlationId correlação técnica sem dados pessoais
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCancellationConfirmationDTO(
    String identifier,
    String proof,
    UUID correlationId) {

  /**
   * Evita expor identificador e prova em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RegistrationCancellationConfirmationDTO[identifier=REDACTED, proof=REDACTED, "
        + "correlationId=" + correlationId + "]";
  }
}
