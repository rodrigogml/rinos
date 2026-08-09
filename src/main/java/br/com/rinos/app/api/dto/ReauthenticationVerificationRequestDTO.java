package br.com.rinos.app.api.dto;

import java.time.Instant;

import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/**
 * Transporta uma prova efêmera para a continuação vinculada à sessão corrente.
 *
 * @param userId identidade da sessão autenticada
 * @param sessionReference referência não autenticadora da sessão corrente
 * @param challengeReference continuação opaca e de uso único
 * @param method método escolhido no desafio
 * @param proof prova transitória que nunca deve ser persistida ou registrada
 * @param occurredAt instante UTC da tentativa
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record ReauthenticationVerificationRequestDTO(
    long userId,
    String sessionReference,
    String challengeReference,
    AuthenticationMethodEnum method,
    String proof,
    Instant occurredAt) {

  /** Valida a forma mínima sem interpretar nem copiar a prova para outro estado. */
  public ReauthenticationVerificationRequestDTO {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (sessionReference == null || sessionReference.isBlank()) {
      throw new IllegalArgumentException("sessionReference must not be blank");
    }
    if (challengeReference == null || challengeReference.isBlank()) {
      throw new IllegalArgumentException("challengeReference must not be blank");
    }
    if (method == null || proof == null || proof.isBlank() || occurredAt == null) {
      throw new IllegalArgumentException("reauthentication proof request is incomplete");
    }
  }

  /** Redige identidade, sessão, continuação e prova em diagnósticos. */
  @Override
  public String toString() {
    return "ReauthenticationVerificationRequestDTO[userId=REDACTED, "
        + "sessionReference=REDACTED, challengeReference=REDACTED, method="
        + method + ", proof=REDACTED, occurredAt=" + occurredAt + "]";
  }
}
