package br.com.rinos.app.backend.module.identity.vo;

import java.util.Set;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationPolicyStatusEnum;

/**
 * Decisão pura da política de reautenticação, sem sessão, usuário ou prova.
 *
 * @author Rodrigo Leitão
 */
public record ReauthenticationPolicyDecisionVO(
    ReauthenticationPolicyStatusEnum status,
    String operationLabelKey,
    Set<AuthenticationMethodEnum> allowedMethods) {

  public ReauthenticationPolicyDecisionVO {
    if (status == null || operationLabelKey == null || operationLabelKey.isBlank()) {
      throw new IllegalArgumentException("reauthentication policy decision is incomplete");
    }
    allowedMethods = allowedMethods == null ? Set.of() : Set.copyOf(allowedMethods);
    if (status == ReauthenticationPolicyStatusEnum.CHALLENGE_REQUIRED
        && allowedMethods.isEmpty()) {
      throw new IllegalArgumentException("challenge requires at least one allowed method");
    }
    if (status != ReauthenticationPolicyStatusEnum.CHALLENGE_REQUIRED
        && !allowedMethods.isEmpty()) {
      throw new IllegalArgumentException("allowed methods belong only to a challenge");
    }
  }
}
