package br.com.rinos.app.backend.module.membership.service;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.api.module.membership.enums.MembershipMutationOperation;
import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;

public record MembershipMutationCommand(
    long actorMembershipId,
    UUID targetMembershipPublicId,
    MembershipMutationOperation operation,
    MembershipRoleType proposedRole,
    long expectedVersion,
    boolean confirmed,
    boolean recentStrongAuthentication,
    String correlationId,
    Instant occurredAt) {

  public MembershipMutationCommand {
    if (actorMembershipId <= 0 || targetMembershipPublicId == null || operation == null
        || expectedVersion < 0 || !confirmed || correlationId == null || correlationId.isBlank()
        || correlationId.length() > 100 || occurredAt == null
        || (operation == MembershipMutationOperation.CHANGE_ROLE) != (proposedRole != null)) {
      throw new IllegalArgumentException("membership mutation command is invalid");
    }
  }

  @Override
  public String toString() {
    return "MembershipMutationCommand[actorMembershipId=REDACTED, targetMembershipPublicId=REDACTED, operation="
        + operation + ", proposedRole=" + proposedRole + ", expectedVersion=" + expectedVersion
        + ", confirmed=" + confirmed + ", recentStrongAuthentication=REDACTED, correlationId="
        + correlationId + ", occurredAt=" + occurredAt + "]";
  }
}
