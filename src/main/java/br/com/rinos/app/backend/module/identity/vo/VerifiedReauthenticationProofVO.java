package br.com.rinos.app.backend.module.identity.vo;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;

/**
 * Evidência sanitizada emitida exclusivamente por um verificador real de reautenticação.
 *
 * @param method método efetivamente comprovado
 * @param userVerification confirmação local WebAuthn ou {@code null} quando inaplicável
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record VerifiedReauthenticationProofVO(
    AuthenticationMethodEnum method,
    Boolean userVerification) {

  /** Exige que o verificador identifique o método comprovado. */
  public VerifiedReauthenticationProofVO {
    if (method == null) {
      throw new IllegalArgumentException("method must not be null");
    }
  }
}
