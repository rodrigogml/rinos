package br.com.rinos.app.api.module.membership.dto;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Contexto confiavel derivado pelo adapter; nunca e desserializado de campos do navegador. */
public record MembershipInvocationContext(
    AuthorizationActor actor,
    long actorMembershipId,
    AuthorizationContext authorizationContext,
    AuthenticationAssurance assurance,
    String correlationId,
    Instant occurredAt) {

  public MembershipInvocationContext {
    actor = Objects.requireNonNull(actor, "actor must not be null");
    authorizationContext = Objects.requireNonNull(
        authorizationContext, "authorizationContext must not be null");
    assurance = Objects.requireNonNull(assurance, "assurance must not be null");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (actor.type() != AuthorizationActorType.HUMAN || actorMembershipId <= 0
        || authorizationContext.scope() != AccessScope.TENANT
        || correlationId == null || correlationId.isBlank() || correlationId.length() > 100) {
      throw new IllegalArgumentException("membership invocation context is invalid");
    }
    correlationId = correlationId.strip();
  }

  @Override
  public String toString() {
    return "MembershipInvocationContext[actor=HUMAN, actorMembershipId=REDACTED, "
        + "authorizationContext=" + authorizationContext + ", assurance=REDACTED, "
        + "correlationId=" + correlationId + ", occurredAt=" + occurredAt + "]";
  }
}
