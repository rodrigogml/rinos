package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/**
 * Transporta os aceites de uma continuação legal pós-autenticação.
 *
 * @param continuationReference referência opaca do fluxo
 * @param acceptedLegalDocumentIds versões apresentadas e aceitas
 * @param occurredAt instante UTC da decisão
 * @author Rodrigo Leitão
 */
public record AuthenticationConsentRequestDTO(
    String continuationReference,
    List<String> acceptedLegalDocumentIds,
    Instant occurredAt) {

  /** Preserva uma fotografia imutável e rejeita duplicidades antes da conversão. */
  public AuthenticationConsentRequestDTO {
    if (continuationReference == null || continuationReference.isBlank()) {
      throw new IllegalArgumentException("continuationReference must not be blank");
    }
    acceptedLegalDocumentIds = acceptedLegalDocumentIds == null
        ? List.of() : List.copyOf(acceptedLegalDocumentIds);
    if (acceptedLegalDocumentIds.stream().anyMatch(id -> id == null || id.isBlank())) {
      throw new IllegalArgumentException("acceptedLegalDocumentIds contains an invalid reference");
    }
    if (new HashSet<>(acceptedLegalDocumentIds).size() != acceptedLegalDocumentIds.size()) {
      throw new IllegalArgumentException("acceptedLegalDocumentIds must not contain duplicates");
    }
    if (occurredAt == null) {
      throw new IllegalArgumentException("occurredAt must not be null");
    }
  }

  /** Redige a continuação em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationConsentRequestDTO[continuationReference=REDACTED, "
        + "acceptedLegalDocumentCount=" + acceptedLegalDocumentIds.size()
        + ", occurredAt=" + occurredAt + "]";
  }
}
