package br.com.rinos.app.backend.module.membership.facade;

import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.enums.AuthorizationExplanationMode;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.TenantFoundationOperations;
import br.com.rinos.app.api.module.access.vo.AuthorizationOperation;
import br.com.rinos.app.api.module.membership.dto.MembershipInvitationRequest;
import br.com.rinos.app.api.module.membership.dto.MembershipInvocationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipMutationRequest;
import br.com.rinos.app.api.module.membership.facade.MembershipFacade;
import br.com.rinos.app.api.module.membership.vo.MembershipInvitationResult;
import br.com.rinos.app.api.module.membership.vo.MembershipMutationResult;
import br.com.rinos.app.backend.module.membership.service.MembershipInvitationService;
import br.com.rinos.app.backend.module.membership.service.MembershipLifecycleService;
import br.com.rinos.app.backend.module.membership.service.MembershipMutationCommand;

/** Autoriza com operacao fechada e somente depois delega ao nucleo persistente. */
@Service
@Lazy
public class MembershipFacadeImpl implements MembershipFacade {

  private static final String CANONICAL_ORIGIN = "membership-facade";

  private final AuthorizationFacade authorization;
  private final MembershipInvitationService invitations;
  private final MembershipLifecycleService lifecycle;

  public MembershipFacadeImpl(
      AuthorizationFacade authorization,
      MembershipInvitationService invitations,
      MembershipLifecycleService lifecycle) {
    this.authorization = authorization;
    this.invitations = invitations;
    this.lifecycle = lifecycle;
  }

  @Override
  public MembershipInvitationResult issue(
      MembershipInvocationContext context, MembershipInvitationRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    require(context, TenantFoundationOperations.inviteMembership());
    return invitations.issue(context.actorMembershipId(), request.accountPublicId(), request.email(),
        request.proposedRole(), CANONICAL_ORIGIN, context.correlationId(), context.occurredAt());
  }

  @Override
  public MembershipInvitationResult resend(
      MembershipInvocationContext context, UUID invitationPublicId) {
    Objects.requireNonNull(invitationPublicId, "invitationPublicId must not be null");
    require(context, TenantFoundationOperations.inviteMembership());
    return invitations.resend(context.actorMembershipId(), invitationPublicId, CANONICAL_ORIGIN,
        context.correlationId(), context.occurredAt());
  }

  @Override
  public MembershipInvitationResult revoke(
      MembershipInvocationContext context, UUID invitationPublicId) {
    Objects.requireNonNull(invitationPublicId, "invitationPublicId must not be null");
    require(context, TenantFoundationOperations.inviteMembership());
    return invitations.revoke(context.actorMembershipId(), invitationPublicId,
        context.correlationId(), context.occurredAt());
  }

  @Override
  public MembershipMutationResult mutate(
      MembershipInvocationContext context, MembershipMutationRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    require(context, TenantFoundationOperations.manageMembership(request.operation()));
    return lifecycle.mutate(new MembershipMutationCommand(
        context.actorMembershipId(), request.targetMembershipPublicId(), request.operation(),
        request.proposedRole(), request.expectedVersion(), request.confirmed(), true,
        context.correlationId(), context.occurredAt()));
  }

  private void require(MembershipInvocationContext context, AuthorizationOperation operation) {
    Objects.requireNonNull(context, "context must not be null");
    authorization.require(new AuthorizationRequest(
        context.actor(), context.actorMembershipId(), context.authorizationContext(),
        operation.code(), operation.requiredKeys(), context.assurance(), operation.sensitive(),
        AuthorizationExplanationMode.NONE));
  }
}
