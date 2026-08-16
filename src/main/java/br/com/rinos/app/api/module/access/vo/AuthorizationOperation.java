package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;
import java.util.Set;

/** Operação protegida tipada publicada pelo módulo consumidor. */
public record AuthorizationOperation(
    String code,
    Set<AccessKeyDescriptor> requiredKeys,
    boolean sensitive) {

  public AuthorizationOperation {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    code = code.strip();
    requiredKeys = requiredKeys == null ? Set.of() : Set.copyOf(requiredKeys);
    if (requiredKeys.isEmpty() || requiredKeys.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("requiredKeys must not be empty or contain null");
    }
  }
}
