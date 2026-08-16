package br.com.rinos.app.api.module.account.vo;

import java.util.Objects;
import java.util.UUID;

/** Identidades estáveis compartilhadas com uma etapa de provisionamento. */
public record AccountBootstrapRequest(
    UUID protocolId,
    UUID accountPublicId,
    UUID tenantPublicId,
    long founderUserId,
    String correlationId) {

  public AccountBootstrapRequest {
    Objects.requireNonNull(protocolId, "protocolId must not be null");
    Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    Objects.requireNonNull(tenantPublicId, "tenantPublicId must not be null");
    if (founderUserId <= 0) {
      throw new IllegalArgumentException("founderUserId must be positive");
    }
    if (correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("correlationId is invalid");
    }
    correlationId = correlationId.strip();
  }
}
