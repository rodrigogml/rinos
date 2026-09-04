package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;

/** Ator mínimo de uma decisão, derivado pela infraestrutura e nunca por campo livre da UI. */
public record AuthorizationActor(
    AuthorizationActorType type,
    Long identityId,
    String systemOrigin) {

  public AuthorizationActor {
    type = Objects.requireNonNull(type, "type must not be null");
    systemOrigin = normalizeOptional(systemOrigin);
    if (type == AuthorizationActorType.HUMAN
        && (identityId == null || identityId <= 0 || systemOrigin != null)) {
      throw new IllegalArgumentException("human actor requires only a positive identityId");
    }
    if (type == AuthorizationActorType.SYSTEM
        && (identityId != null || systemOrigin == null)) {
      throw new IllegalArgumentException("system actor requires only a systemOrigin");
    }
  }

  public static AuthorizationActor human(long identityId) {
    return new AuthorizationActor(AuthorizationActorType.HUMAN, identityId, null);
  }

  public static AuthorizationActor system(String systemOrigin) {
    return new AuthorizationActor(AuthorizationActorType.SYSTEM, null, systemOrigin);
  }

  @Override
  public String toString() {
    return "AuthorizationActor[type=" + type + ", identityId=REDACTED, systemOrigin=REDACTED]";
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
