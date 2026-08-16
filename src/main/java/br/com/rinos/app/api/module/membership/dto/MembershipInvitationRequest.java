package br.com.rinos.app.api.module.membership.dto;

import java.util.Objects;
import java.util.UUID;

import br.com.rinos.app.api.module.membership.enums.MembershipRoleType;

/** Dados administrativos de um convite, sem ator, tenant tecnico ou chave escolhida pelo cliente. */
public record MembershipInvitationRequest(
    UUID accountPublicId,
    String email,
    MembershipRoleType proposedRole) {

  public MembershipInvitationRequest {
    accountPublicId = Objects.requireNonNull(accountPublicId, "accountPublicId must not be null");
    proposedRole = Objects.requireNonNull(proposedRole, "proposedRole must not be null");
    if (email == null || email.isBlank() || email.length() > 320) {
      throw new IllegalArgumentException("invitation email is invalid");
    }
    email = email.strip();
  }

  @Override
  public String toString() {
    return "MembershipInvitationRequest[accountPublicId=REDACTED, email=REDACTED, proposedRole="
        + proposedRole + "]";
  }
}
