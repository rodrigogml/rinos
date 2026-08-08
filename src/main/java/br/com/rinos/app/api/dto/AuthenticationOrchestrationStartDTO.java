package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/**
 * Entrega ao orquestrador um primeiro fator que já foi validado pelo serviço especializado.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationOrchestrationStartDTO(
    long userId,
    AuthenticationMethodEnum primaryMethod,
    AuthenticationAssuranceEnum requiredAssurance,
    Set<AuthenticationMethodEnum> permittedMethods,
    boolean persistentLoginRequested,
    Instant verifiedAt,
    Boolean userVerification,
    Instant issuedAt,
    Instant expiresAt,
    UUID correlationId) {

  public AuthenticationOrchestrationStartDTO {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    Objects.requireNonNull(primaryMethod, "primaryMethod must not be null");
    Objects.requireNonNull(requiredAssurance, "requiredAssurance must not be null");
    permittedMethods = permittedMethods == null ? Set.of() : Set.copyOf(permittedMethods);
    Objects.requireNonNull(verifiedAt, "verifiedAt must not be null");
    Objects.requireNonNull(issuedAt, "issuedAt must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
    if (expiresAt.isBefore(issuedAt) || expiresAt.equals(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
  }
}
