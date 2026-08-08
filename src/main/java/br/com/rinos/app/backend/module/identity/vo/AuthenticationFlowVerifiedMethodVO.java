package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;

/**
 * Evidência sanitizada e imutável de um método comprovado no fluxo.
 *
 * @param method método comprovado
 * @param verifiedAt instante UTC da comprovação
 * @param userVerification verificação local WebAuthn ou {@code null} quando inaplicável
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationFlowVerifiedMethodVO(
    AuthenticationMethodEnum method,
    Instant verifiedAt,
    Boolean userVerification) {

  public AuthenticationFlowVerifiedMethodVO {
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
  }
}
