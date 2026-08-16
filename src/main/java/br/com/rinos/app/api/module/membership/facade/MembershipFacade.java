package br.com.rinos.app.api.module.membership.facade;

import java.util.UUID;

import br.com.rinos.app.api.module.membership.dto.MembershipInvitationRequest;
import br.com.rinos.app.api.module.membership.dto.MembershipInvocationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipMutationRequest;
import br.com.rinos.app.api.module.membership.vo.MembershipInvitationResult;
import br.com.rinos.app.api.module.membership.vo.MembershipMutationResult;

/** Fronteira que torna autorizacao contextual obrigatoria antes da persistencia de membership. */
public interface MembershipFacade {
  MembershipInvitationResult issue(
      MembershipInvocationContext context, MembershipInvitationRequest request);
  MembershipInvitationResult resend(MembershipInvocationContext context, UUID invitationPublicId);
  MembershipInvitationResult revoke(MembershipInvocationContext context, UUID invitationPublicId);
  MembershipMutationResult mutate(
      MembershipInvocationContext context, MembershipMutationRequest request);
}
