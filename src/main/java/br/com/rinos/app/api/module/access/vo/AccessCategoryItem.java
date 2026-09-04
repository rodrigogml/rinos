package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;

/** Categoria interna para navegação; a UI renderiza exclusivamente sua chave localizada. */
public record AccessCategoryItem(
    String internalReference,
    String parentInternalReference,
    String nameI18nKey) {

  public AccessCategoryItem {
    internalReference = requireText(internalReference, "internalReference");
    parentInternalReference = normalize(parentInternalReference);
    nameI18nKey = requireText(nameI18nKey, "nameI18nKey");
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    return value.strip();
  }
}
