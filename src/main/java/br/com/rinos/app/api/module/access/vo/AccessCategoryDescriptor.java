package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;
import java.util.regex.Pattern;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Categoria hierárquica usada somente para navegação e apresentação. */
public record AccessCategoryDescriptor(
    String code,
    String parentCode,
    AccessScope scope,
    String nameI18nKey) {

  private static final Pattern CODE_PATTERN =
      Pattern.compile("(?:global|tenant)(?:\\.[a-z][a-z0-9-]*)+");

  public AccessCategoryDescriptor {
    code = requireCode(code, "code");
    parentCode = parentCode == null ? null : requireCode(parentCode, "parentCode");
    scope = Objects.requireNonNull(scope, "scope must not be null");
    nameI18nKey = requireText(nameI18nKey, "nameI18nKey");
    String expectedPrefix = scope == AccessScope.GLOBAL ? "global." : "tenant.";
    if (!code.startsWith(expectedPrefix)
        || parentCode != null && !parentCode.startsWith(expectedPrefix)) {
      throw new IllegalArgumentException("category scope is incompatible with its code");
    }
    if (code.equals(parentCode)) {
      throw new IllegalArgumentException("category must not be its own parent");
    }
  }

  private static String requireCode(String value, String field) {
    String normalized = requireText(value, field);
    if (!CODE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException(field + " has an invalid format");
    }
    return normalized;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.strip();
  }
}
