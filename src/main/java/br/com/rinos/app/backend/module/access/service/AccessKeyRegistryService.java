package br.com.rinos.app.backend.module.access.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.access.contributor.AccessKeyContributor;
import br.com.rinos.app.api.module.access.vo.AccessCategoryDescriptor;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;

/**
 * Agrega contributors e publica uma fotografia validada e imutável do catálogo em código.
 *
 * <p>A sincronização persistente será adicionada com o schema global. Esta classe já fixa as
 * invariantes fail-fast que antecedem qualquer escrita.</p>
 */
@Service
@org.springframework.context.annotation.Lazy
public class AccessKeyRegistryService {

  private final Map<String, AccessCategoryDescriptor> categoriesByCode;
  private final Map<String, AccessKeyDescriptor> keysByCode;

  public AccessKeyRegistryService(List<AccessKeyContributor> contributors) {
    List<AccessKeyContributor> safeContributors = contributors == null ? List.of() : contributors;
    Map<String, AccessCategoryDescriptor> categories = new LinkedHashMap<>();
    Map<String, AccessKeyDescriptor> keys = new LinkedHashMap<>();

    safeContributors.stream()
        .sorted(Comparator.comparing(AccessKeyContributor::moduleCode))
        .forEach(contributor -> registerContributor(contributor, categories, keys));
    validateCategoryTree(categories);
    validateKeyCategories(categories, keys);
    categoriesByCode = Map.copyOf(categories);
    keysByCode = Map.copyOf(keys);
  }

  public Collection<AccessCategoryDescriptor> categories() {
    return categoriesByCode.values().stream()
        .sorted(Comparator.comparing(AccessCategoryDescriptor::code))
        .toList();
  }

  public Collection<AccessKeyDescriptor> accessKeys() {
    return keysByCode.values().stream()
        .sorted(Comparator.comparing(AccessKeyDescriptor::code))
        .toList();
  }

  public Optional<AccessKeyDescriptor> find(String code) {
    return Optional.ofNullable(keysByCode.get(code));
  }

  public AccessKeyDescriptor require(String code) {
    AccessKeyDescriptor descriptor = keysByCode.get(code);
    if (descriptor == null) {
      throw new IllegalArgumentException("unknown access key");
    }
    return descriptor;
  }

  private static void registerContributor(
      AccessKeyContributor contributor,
      Map<String, AccessCategoryDescriptor> categories,
      Map<String, AccessKeyDescriptor> keys) {
    Objects.requireNonNull(contributor, "access key contributor must not be null");
    String moduleCode = requireText(contributor.moduleCode(), "contributor moduleCode");
    registerAll(
        safeCollection(contributor.categories(), "categories"),
        AccessCategoryDescriptor::code,
        categories,
        "category");
    for (AccessKeyDescriptor key : safeCollection(contributor.accessKeys(), "accessKeys")) {
      Objects.requireNonNull(key, "access key descriptor must not be null");
      if (!moduleCode.equals(key.ownerModule())) {
        throw new IllegalStateException("access key owner differs from contributor module");
      }
      register(keys, key.code(), key, "access key");
    }
  }

  private static void validateCategoryTree(Map<String, AccessCategoryDescriptor> categories) {
    for (AccessCategoryDescriptor category : categories.values()) {
      if (category.parentCode() == null) {
        continue;
      }
      AccessCategoryDescriptor parent = categories.get(category.parentCode());
      if (parent == null || parent.scope() != category.scope()) {
        throw new IllegalStateException("category parent is missing or has incompatible scope");
      }
      String cursor = parent.parentCode();
      int remaining = categories.size();
      while (cursor != null && remaining-- > 0) {
        if (cursor.equals(category.code())) {
          throw new IllegalStateException("category hierarchy contains a cycle");
        }
        AccessCategoryDescriptor ancestor = categories.get(cursor);
        cursor = ancestor == null ? null : ancestor.parentCode();
      }
      if (remaining < 0) {
        throw new IllegalStateException("category hierarchy contains a cycle");
      }
    }
  }

  private static void validateKeyCategories(
      Map<String, AccessCategoryDescriptor> categories,
      Map<String, AccessKeyDescriptor> keys) {
    for (AccessKeyDescriptor key : keys.values()) {
      AccessCategoryDescriptor category = categories.get(key.categoryCode());
      if (category == null || category.scope() != key.scope()) {
        throw new IllegalStateException("access key category is missing or has incompatible scope");
      }
    }
  }

  private static <T> void registerAll(
      Collection<T> values,
      java.util.function.Function<T, String> codeExtractor,
      Map<String, T> target,
      String kind) {
    for (T value : values) {
      Objects.requireNonNull(value, kind + " descriptor must not be null");
      register(target, codeExtractor.apply(value), value, kind);
    }
  }

  private static <T> void register(Map<String, T> target, String code, T value, String kind) {
    T existing = target.putIfAbsent(code, value);
    if (existing != null && !existing.equals(value)) {
      throw new IllegalStateException("incompatible " + kind + " descriptor: " + code);
    }
  }

  private static <T> Collection<T> safeCollection(Collection<T> values, String field) {
    if (values == null) {
      throw new IllegalStateException(field + " must not be null");
    }
    return values;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(field + " must not be blank");
    }
    return value.strip();
  }
}
