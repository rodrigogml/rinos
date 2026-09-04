package br.com.rinos.app.api.module.access.dto;

import java.util.Objects;

import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.access.vo.AuthorizationWorkspaceContext;

/** Dados mínimos registráveis de um trabalho originado por usuário, sem decisão congelada. */
public record UserInitiatedAuthorizationJob(
    AuthorizationActor actor,
    AuthorizationWorkspaceContext workspace,
    AuthorizationOperation operation,
    AuthenticationAssurance submittedAssurance) {

  public UserInitiatedAuthorizationJob {
    actor = Objects.requireNonNull(actor, "actor must not be null");
    workspace = Objects.requireNonNull(workspace, "workspace must not be null");
    operation = Objects.requireNonNull(operation, "operation must not be null");
    submittedAssurance = Objects.requireNonNull(
        submittedAssurance, "submittedAssurance must not be null");
    if (actor.type() != br.com.rinos.app.api.module.access.enums.AuthorizationActorType.HUMAN) {
      throw new IllegalArgumentException("user initiated job requires a human actor");
    }
    for (var key : operation.requiredKeys()) {
      if (key.scope() != workspace.context().scope()) {
        throw new IllegalArgumentException("job operation scope is incompatible with workspace");
      }
    }
  }
}
