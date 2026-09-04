package br.com.rinos.app.api.module.access.vo;

import java.util.List;
import java.util.Objects;

/** Fotografia consistente da central de acesso para um único contexto. */
public record AccessAdministrationSnapshot(
    AuthorizationContext context,
    long revision,
    AccessAdministrationCapabilities capabilities,
    List<AccessCategoryItem> categories,
    List<AccessKeyItem> keys,
    List<AccessGroupItem> groups,
    List<AccessSubjectItem> subjects,
    List<AccessGroupSubjectItem> groupSubjects,
    List<AccessRuleItem> rules) {

  public AccessAdministrationSnapshot {
    context = Objects.requireNonNull(context, "context must not be null");
    capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
    if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
    categories = List.copyOf(categories);
    keys = List.copyOf(keys);
    groups = List.copyOf(groups);
    subjects = List.copyOf(subjects);
    groupSubjects = List.copyOf(groupSubjects);
    rules = List.copyOf(rules);
  }
}
