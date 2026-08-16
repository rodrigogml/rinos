package br.com.rinos.app.api.module.membership.dto;

import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;

/** Alteracao solicitada; ator, garantia e contexto sao acrescentados pela fronteira confiavel. */
public record MembershipMutationRequest(
    UUID targetMembershipPublicId,
    MembershipMutationOperation operation,
    MembershipRoleType proposedRole,
    long expectedVersion,
    boolean confirmed) {

  public MembershipMutationRequest {
    targetMembershipPublicId = Objects.requireNonNull(
        targetMembershipPublicId, "targetMembershipPublicId must not be null");
    operation = Objects.requireNonNull(operation, "operation must not be null");
    if (expectedVersion < 0 || !confirmed
        || (operation == MembershipMutationOperation.CHANGE_ROLE) != (proposedRole != null)) {
      throw new IllegalArgumentException("membership mutation request is invalid");
    }
  }

  @Override
  public String toString() {
    return "MembershipMutationRequest[targetMembershipPublicId=REDACTED, operation=" + operation
        + ", proposedRole=" + proposedRole + ", expectedVersion=" + expectedVersion
        + ", confirmed=" + confirmed + "]";
  }
}
