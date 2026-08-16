package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceStatus;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;

/** Origem minimizada de uma regra considerada na decisão. */
public record AuthorizationRuleSource(
    AuthorizationSourceType type,
    String sourceReference,
    AccessRuleEffect effect,
    AuthorizationSourceStatus status,
    Instant validFrom,
    Instant validUntil) {

  public AuthorizationRuleSource {
    type = Objects.requireNonNull(type, "type must not be null");
    if (sourceReference == null || sourceReference.isBlank()) {
      throw new IllegalArgumentException("sourceReference must not be blank");
    }
    sourceReference = sourceReference.strip();
    effect = Objects.requireNonNull(effect, "effect must not be null");
    status = Objects.requireNonNull(status, "status must not be null");
    if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
      throw new IllegalArgumentException("validUntil must be after validFrom");
    }
  }

  @Override
  public String toString() {
    return "AuthorizationRuleSource[type=" + type + ", sourceReference=REDACTED, effect="
        + effect + ", status=" + status + ", validFrom=" + validFrom
        + ", validUntil=" + validUntil + "]";
  }
}
