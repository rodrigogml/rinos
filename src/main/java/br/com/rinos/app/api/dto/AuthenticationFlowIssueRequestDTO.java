package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/**
 * Solicita uma continuação opaca sem permitir que o chamador forneça sua referência.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationFlowIssueRequestDTO(
    Long userId,
    AuthenticationFlowPurposeEnum purpose,
    AuthenticationMethodEnum primaryMethod,
    AuthenticationAssuranceEnum requiredAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    boolean persistentLoginRequested,
    Instant issuedAt,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationFlowIssueRequestDTO {
    purpose = Objects.requireNonNull(purpose, "purpose must not be null");
    requiredAssurance = Objects.requireNonNull(
        requiredAssurance,
        "requiredAssurance must not be null");
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
    issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
  }
}
