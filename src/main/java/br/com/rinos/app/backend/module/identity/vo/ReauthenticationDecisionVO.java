package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Set;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationStatusEnum;

/**
 * Resultado interno seguro de uma etapa da reautenticação.
 *
 * <p>Somente um desafio carrega referência opaca, validade, rótulo humano e métodos. Os
 * demais resultados não transportam estado reutilizável.
 *
 * @param status resultado da etapa
 * @param challengeReference referência opaca ou {@code null}
 * @param operationLabelKey chave i18n humana ou {@code null}
 * @param expiresAt vencimento do desafio ou {@code null}
 * @param allowedMethods métodos oferecidos pelo desafio
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record ReauthenticationDecisionVO(
    ReauthenticationStatusEnum status,
    String challengeReference,
    String operationLabelKey,
    Instant expiresAt,
    Set<AuthenticationMethodEnum> allowedMethods) {

  /** Valida que dados transitórios existam exclusivamente em um desafio. */
  public ReauthenticationDecisionVO {
    if (status == null) {
      throw new IllegalArgumentException("reauthentication status must not be null");
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
      throw new IllegalArgumentException("terminal reauthentication must not expose challenge data");
    }
  }

  /** @return resultado sem dados de desafio */
  public static ReauthenticationDecisionVO terminal(ReauthenticationStatusEnum status) {
    return new ReauthenticationDecisionVO(status, null, null, null, Set.of());
  }
}
