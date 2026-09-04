package br.com.rinos.app.api.module.access.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;

/** Fotografia corrente e não autenticadora da garantia comprovada pela sessão. */
public record AuthenticationAssurance(
    AuthenticationAssuranceEnum level,
    Set<AuthenticationMethodEnum> methods,
    Instant authenticatedAt,
    Instant lastStrongAuthenticationAt) {

  public AuthenticationAssurance {
    level = Objects.requireNonNull(level, "level must not be null");
    methods = methods == null ? Set.of() : Set.copyOf(methods);
    authenticatedAt = Objects.requireNonNull(authenticatedAt, "authenticatedAt must not be null");
    if (methods.isEmpty()) {
      throw new IllegalArgumentException("methods must not be empty");
    }
  }
}
