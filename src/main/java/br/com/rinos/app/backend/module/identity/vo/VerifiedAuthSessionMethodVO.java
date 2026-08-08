package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;

/**
 * Evidência sanitizada de um método que contribuiu para a garantia da sessão.
 *
 * @param method método comprovado
 * @param verifiedAt instante UTC da comprovação
 * @param userVerification confirmação local do autenticador ou {@code null} quando inaplicável
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record VerifiedAuthSessionMethodVO(
    AuthenticationMethodEnum method,
    Instant verifiedAt,
    Boolean userVerification) {

  public VerifiedAuthSessionMethodVO {
    Objects.requireNonNull(method, "method must not be null");
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
  }
}
