package br.com.rinos.app.api.module.access.vo;

import java.util.List;
import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceStatus;

/** Resultado individual e explicável de uma chave obrigatória. */
public record AuthorizationKeyResult(
    AccessKeyDescriptor key,
    boolean allowed,
    List<AuthorizationRuleSource> permitSources,
    List<AuthorizationRuleSource> blockingSources,
    boolean missingPermit,
    List<AuthorizationRuleSource> ignoredSources) {

  public AuthorizationKeyResult {
    key = Objects.requireNonNull(key, "key must not be null");
    permitSources = permitSources == null ? List.of() : List.copyOf(permitSources);
    blockingSources = blockingSources == null ? List.of() : List.copyOf(blockingSources);
    ignoredSources = ignoredSources == null ? List.of() : List.copyOf(ignoredSources);
    if (permitSources.stream().anyMatch(source -> source.effect() != AccessRuleEffect.PERMITIR
        || source.status() != AuthorizationSourceStatus.CURRENT)) {
      throw new IllegalArgumentException("permitSources must contain only current permits");
    }
    if (blockingSources.stream().anyMatch(source -> source.effect() != AccessRuleEffect.BLOQUEAR
        || source.status() != AuthorizationSourceStatus.CURRENT)) {
      throw new IllegalArgumentException("blockingSources must contain only current blocks");
    }
    if (ignoredSources.stream().anyMatch(
        source -> source.status() == AuthorizationSourceStatus.CURRENT)) {
      throw new IllegalArgumentException("ignoredSources must not contain current sources");
    }
    if (allowed != (!permitSources.isEmpty() && blockingSources.isEmpty())
        || missingPermit != permitSources.isEmpty()) {
      throw new IllegalArgumentException("key result is inconsistent");
    }
  }
}
