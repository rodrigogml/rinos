package br.com.rinos.app.api.module.access.vo;

import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;

/** Identidade, associacao, contexto e garantia derivados pela infraestrutura autenticada. */
public record HumanAuthorizationContext(
    AuthorizationActor actor,
    Long membershipId,
    AuthorizationContext context,
    AuthenticationAssurance assurance) {

  public HumanAuthorizationContext {
    actor = Objects.requireNonNull(actor, "actor must not be null");
    context = Objects.requireNonNull(context, "context must not be null");
    assurance = Objects.requireNonNull(assurance, "assurance must not be null");
    if (actor.type() != AuthorizationActorType.HUMAN
        || context.scope() == AccessScope.TENANT && (membershipId == null || membershipId <= 0)
        || context.scope() == AccessScope.GLOBAL && membershipId != null) {
      throw new IllegalArgumentException("human authorization context is inconsistent");
    }
  }

  @Override
  public String toString() {
    return "HumanAuthorizationContext[actor=HUMAN, membershipId=REDACTED, context=" + context
        + ", assurance=REDACTED]";
  }
}
