package br.com.rinos.app.api.module.access.dto;

import java.util.Objects;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationActorType;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;

/** Consulta administrativa que separa o consulente autenticado da operação explicada. */
public record AccessExplanationRequest(
    AuthorizationActor requester,
    Long requesterMembershipId,
    AuthenticationAssurance requesterAssurance,
    AuthorizationRequest targetRequest) {

  public AccessExplanationRequest {
    requester = Objects.requireNonNull(requester, "requester must not be null");
    requesterAssurance = Objects.requireNonNull(
        requesterAssurance, "requesterAssurance must not be null");
    targetRequest = Objects.requireNonNull(targetRequest, "targetRequest must not be null");
    if (requester.type() != AuthorizationActorType.HUMAN) {
      throw new IllegalArgumentException("access explanation requester must be human");
    }
    if (targetRequest.context().scope() == AccessScope.TENANT
        && (requesterMembershipId == null || requesterMembershipId <= 0)) {
      throw new IllegalArgumentException(
          "tenant access explanation requires requester membership");
    }
    if (targetRequest.context().scope() == AccessScope.GLOBAL
        && requesterMembershipId != null) {
      throw new IllegalArgumentException(
          "global access explanation must not carry requester membership");
    }
  }

  @Override
  public String toString() {
    return "AccessExplanationRequest[requester=HUMAN, requesterMembershipId=REDACTED, "
        + "requesterAssurance=REDACTED, targetRequest=" + targetRequest + "]";
  }
}
