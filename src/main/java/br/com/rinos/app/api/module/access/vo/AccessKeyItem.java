package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationState;

/** Chave da matriz; a referência técnica nunca deve ser usada como texto de interface. */
public record AccessKeyItem(
    String internalReference,
    String categoryInternalReference,
    String nameI18nKey,
    String descriptionI18nKey,
    AccessAdministrationState state,
    boolean minimumAdministrative) {

  public AccessKeyItem {
    internalReference = requireText(internalReference, "internalReference");
    categoryInternalReference = requireText(categoryInternalReference, "categoryInternalReference");
    nameI18nKey = requireText(nameI18nKey, "nameI18nKey");
    descriptionI18nKey = requireText(descriptionI18nKey, "descriptionI18nKey");
    state = Objects.requireNonNull(state, "state must not be null");
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    return value.strip();
  }
}
