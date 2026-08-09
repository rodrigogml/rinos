package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationSessionLifecycleStatusEnum;

/**
 * Resultado público do lifecycle sem material autenticador.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record AuthenticationSessionLifecycleResultVO(
    AuthenticationSessionLifecycleStatusEnum status,
    String sessionReference,
    RinosUserPrincipalVO principal,
    boolean persistent,
    Instant absoluteExpiresAt) {

  public AuthenticationSessionLifecycleResultVO {
    Objects.requireNonNull(status, "status must not be null");
    boolean usable = status == AuthenticationSessionLifecycleStatusEnum.PREPARED
        || status == AuthenticationSessionLifecycleStatusEnum.ACTIVE;
    if (usable && (sessionReference == null || sessionReference.isBlank()
        || principal == null || absoluteExpiresAt == null)) {
      throw new IllegalArgumentException("usable session result is incomplete");
    }
    if (!usable && (sessionReference != null || principal != null || absoluteExpiresAt != null)) {
      throw new IllegalArgumentException("terminal session result must not expose session data");
    }
  }

  /** Redige a referência e a identidade em diagnósticos. */
  @Override
  public String toString() {
    return "AuthenticationSessionLifecycleResultVO[status=" + status
        + ", sessionReference=REDACTED, principal=REDACTED, persistent=" + persistent
        + ", absoluteExpiresAt=" + absoluteExpiresAt + "]";
  }
}
