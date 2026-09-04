package br.com.rinos.app.api.module.access.vo;

import java.util.regex.Pattern;

/** Rastreabilidade de uma chave para requisito consumidor. */
public record AccessKeyRequirement(String featureCode, String requirementCode) {

  private static final Pattern FEATURE_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");
  private static final Pattern REQUIREMENT_PATTERN =
      Pattern.compile("FR-[A-Z0-9]+(?:-[A-Z0-9]+)+");

  public AccessKeyRequirement {
    featureCode = requireText(featureCode, "featureCode");
    requirementCode = requireText(requirementCode, "requirementCode");
    if (!FEATURE_PATTERN.matcher(featureCode).matches()) {
      throw new IllegalArgumentException("featureCode has an invalid format");
    }
    if (!REQUIREMENT_PATTERN.matcher(requirementCode).matches()) {
      throw new IllegalArgumentException("requirementCode must be one exact requirement ID");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.strip();
  }
}
