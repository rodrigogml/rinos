package br.com.rinos.app.api.module.membership.vo;

import java.util.UUID;

import br.com.rinos.app.api.module.membership.enums.MembershipMutationResultStatus;

public record MembershipMutationResult(
    MembershipMutationResultStatus status,
    UUID membershipPublicId,
    Long version,
    Long contextRevision,
    String safeReasonCode) {

  @Override
  public String toString() {
    return "MembershipMutationResult[status=" + status + ", membershipPublicId=REDACTED, version="
        + version + ", contextRevision=" + contextRevision + ", safeReasonCode=" + safeReasonCode + "]";
  }
}
