package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOperationStatusEnum;

/**
 * Resultado opaco do fluxo, sem entidade, hash ou dado de credencial.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationFlowResultVO(
    AuthenticationOperationStatusEnum status,
    String reference,
    Long userId,
    AuthenticationFlowPurposeEnum purpose,
    AuthenticationMethodEnum primaryMethod,
    AuthenticationAssuranceEnum requiredAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    boolean persistentLoginRequested,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationFlowResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
  }

  @Override
  public String toString() {
    return "AuthenticationFlowResultVO[status=" + status
        + ", reference=REDACTED, userId=" + userId + ", purpose=" + purpose
        + ", primaryMethod=" + primaryMethod + ", requiredAssurance=" + requiredAssurance
        + ", permittedMethods=" + permittedMethods + ", persistentLoginRequested="
        + persistentLoginRequested + ", expiresAt=" + expiresAt + ", correlationId="
        + correlationId + "]";
  }
}
