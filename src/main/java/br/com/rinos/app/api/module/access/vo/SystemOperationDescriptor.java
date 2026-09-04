package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;
import java.util.Set;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Capacidade sistêmica imutável e exata publicada pelo módulo proprietário. */
public record SystemOperationDescriptor(
    String origin, String operationCode, AccessScope scope,
    Set<String> requiredKeyCodes, boolean active) {

  public SystemOperationDescriptor {
    origin = requireText(origin, "origin");
    operationCode = requireText(operationCode, "operationCode");
    scope = Objects.requireNonNull(scope, "scope must not be null");
    requiredKeyCodes = requiredKeyCodes == null ? Set.of() : Set.copyOf(requiredKeyCodes);
    if (requiredKeyCodes.isEmpty() || requiredKeyCodes.stream().anyMatch(
        value -> value == null || value.isBlank())) {
      throw new IllegalArgumentException("requiredKeyCodes must not be empty");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    return value.strip();
  }
}
