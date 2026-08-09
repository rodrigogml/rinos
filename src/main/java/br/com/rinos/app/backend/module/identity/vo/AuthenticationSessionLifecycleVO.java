package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationSessionLifecycleStatusEnum;

/**
 * Resultado seguro de preparação, publicação ou validação de sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record AuthenticationSessionLifecycleVO(
    AuthenticationSessionLifecycleStatusEnum status,
    UUID sessionReference,
    Long userId,
    String email,
    boolean persistent,
    Instant absoluteExpiresAt,
    UUID correlationId) {

  public AuthenticationSessionLifecycleVO {
    Objects.requireNonNull(status, "status must not be null");
  }

  /** Cria um resultado terminal sem expor dados da identidade. */
  public static AuthenticationSessionLifecycleVO terminal(
      AuthenticationSessionLifecycleStatusEnum status) {
    return new AuthenticationSessionLifecycleVO(
        status, null, null, null, false, null, null);
  }

  /** Redige identidade e referências em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationSessionLifecycleVO[status=" + status
        + ", sessionReference=REDACTED, userId=REDACTED, email=REDACTED"
        + ", persistent=" + persistent + ", absoluteExpiresAt=" + absoluteExpiresAt
        + ", correlationId=" + correlationId + "]";
  }
}
