package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Set;

import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.ReauthenticationStatusEnum;

/**
 * Resultado público seguro de uma etapa de reautenticação.
 *
 * @param status resultado fechado
 * @param challengeReference referência opaca presente apenas no desafio
 * @param operationLabelKey chave i18n humana presente apenas no desafio
 * @param expiresAt vencimento presente apenas no desafio
 * @param allowedMethods métodos efetivamente verificáveis no desafio
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record ReauthenticationResultVO(
    ReauthenticationStatusEnum status,
    String challengeReference,
    String operationLabelKey,
    Instant expiresAt,
    Set<AuthenticationMethodEnum> allowedMethods) {

  /** Preserva a fotografia e a coerência entre resultado e dados do desafio. */
  public ReauthenticationResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    allowedMethods = allowedMethods == null ? Set.of() : Set.copyOf(allowedMethods);
    boolean challenge = status == ReauthenticationStatusEnum.CHALLENGE_REQUIRED;
    boolean completeChallenge = challenge
        && challengeReference != null
        && !challengeReference.isBlank()
        && operationLabelKey != null
        && !operationLabelKey.isBlank()
        && expiresAt != null
        && !allowedMethods.isEmpty();
    if (challenge != completeChallenge) {
      throw new IllegalArgumentException("reauthentication challenge is inconsistent");
    }
    if (!challenge && (challengeReference != null
        || operationLabelKey != null
        || expiresAt != null
        || !allowedMethods.isEmpty())) {
      throw new IllegalArgumentException("terminal result must not expose challenge data");
    }
  }

  /** Redige a continuação opaca em diagnósticos. */
  @Override
  public String toString() {
    return "ReauthenticationResultVO[status=" + status
        + ", challengeReference=REDACTED, operationLabelKey=" + operationLabelKey
        + ", expiresAt=" + expiresAt + ", allowedMethods=" + allowedMethods + "]";
  }
}
