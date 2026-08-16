package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;

/** Metadados obrigatórios e auditáveis de uma mutação ACL. */
public record AccessMutationMetadata(
    Long actorUserId,
    String systemOrigin,
    String reason,
    String correlationId,
    Instant occurredAt) {

  public AccessMutationMetadata {
    systemOrigin = normalize(systemOrigin);
    reason = normalize(reason);
    correlationId = normalize(correlationId);
    if ((actorUserId == null) == (systemOrigin == null)
        || actorUserId != null && actorUserId <= 0
        || correlationId == null || correlationId.length() > 100
        || reason != null && reason.length() > 500
        || systemOrigin != null && systemOrigin.length() > 100
        || occurredAt == null) {
      throw new IllegalArgumentException("access mutation metadata is inconsistent");
    }
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
