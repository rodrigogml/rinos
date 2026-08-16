package br.com.rinos.app.api.module.plans.vo;

import java.util.Objects;
import java.util.regex.Pattern;

import br.com.rinos.app.api.module.plans.enums.ContractScope;

/** Requisito estável de direito com titularidade explícita. */
public record EntitlementRequirement(ContractScope subjectScope, String code) {

  private static final Pattern CODE_PATTERN =
      Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*){2,}");

  public EntitlementRequirement {
    subjectScope = Objects.requireNonNull(subjectScope, "subjectScope must not be null");
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    code = code.strip();
    if (!CODE_PATTERN.matcher(code).matches()) {
      throw new IllegalArgumentException("code has an invalid format");
    }
  }
}
