package br.com.rinos.app.api.module.access.dto;

import java.util.Objects;
import java.util.Set;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.vo.AccessKeyDescriptor;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Requisição canônica e contextual de decisão de acesso. */
public record AuthorizationRequest(
    AuthorizationActor actor,
    Long membershipId,
    AuthorizationContext context,
    String operationCode,
    Set<AccessKeyDescriptor> requiredKeys,
    AuthenticationAssurance assurance,
    boolean sensitive,
    AuthorizationExplanationMode explanationMode) {

  public AuthorizationRequest {
    actor = Objects.requireNonNull(actor, "actor must not be null");
    context = Objects.requireNonNull(context, "context must not be null");
    operationCode = requireText(operationCode, "operationCode");
    requiredKeys = requiredKeys == null ? Set.of() : Set.copyOf(requiredKeys);
    explanationMode = Objects.requireNonNull(explanationMode, "explanationMode must not be null");
    if (requiredKeys.isEmpty()) {
      throw new IllegalArgumentException("requiredKeys must not be empty");
    }
    AccessScope contextScope = context.scope();
    if (requiredKeys.stream().anyMatch(key -> key.scope() != contextScope)) {
      throw new IllegalArgumentException("required key scope is incompatible with context");
    }
    boolean human = actor.type() == AuthorizationActorType.HUMAN;
    if (human && assurance == null) {
      throw new IllegalArgumentException("human actor requires assurance");
    }
    if (!human && assurance != null) {
      throw new IllegalArgumentException("system actor must not carry human assurance");
    }
    if (contextScope == AccessScope.TENANT && human
        && (membershipId == null || membershipId <= 0)) {
      throw new IllegalArgumentException("human tenant request requires a positive membershipId");
    }
    if ((contextScope == AccessScope.GLOBAL || !human) && membershipId != null) {
      throw new IllegalArgumentException("membershipId is only valid for human tenant requests");
    }
  }

  @Override
  public String toString() {
    return "AuthorizationRequest[actor=" + actor.type()
        + ", membershipId=REDACTED, context=" + context
        + ", operationCode=" + operationCode + ", requiredKeyCount=" + requiredKeys.size()
        + ", assurance=REDACTED, sensitive=" + sensitive
        + ", explanationMode=" + explanationMode + "]";
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.strip();
  }
}
