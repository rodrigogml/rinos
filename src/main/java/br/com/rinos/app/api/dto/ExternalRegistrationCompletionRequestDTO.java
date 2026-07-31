package br.com.rinos.app.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Solicita a conclusão de um cadastro iniciado por identidade externa validada.
 *
 * @param registrationReference referência opaca de uso único
 * @param acceptedLegalDocumentIds versões legais explicitamente aceitas
 * @param correlationId correlação técnica sem dados pessoais
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record ExternalRegistrationCompletionRequestDTO(
    String registrationReference,
    List<String> acceptedLegalDocumentIds,
    UUID correlationId) {

  /**
   * Preserva uma fotografia imutável dos aceites recebidos.
   */
  public ExternalRegistrationCompletionRequestDTO {
    acceptedLegalDocumentIds = acceptedLegalDocumentIds == null
        ? List.of()
        : List.copyOf(acceptedLegalDocumentIds);
  }

  /**
   * Evita expor a referência externa de uso único em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "ExternalRegistrationCompletionRequestDTO[registrationReference=REDACTED, "
        + "acceptedLegalDocumentCount=" + acceptedLegalDocumentIds.size()
        + ", correlationId=" + correlationId + "]";
  }
}
