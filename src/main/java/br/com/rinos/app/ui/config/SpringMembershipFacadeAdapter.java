package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.vaadin.flow.component.UI;

import br.com.rinos.app.api.module.membership.dto.MembershipInvitationRequest;
import br.com.rinos.app.api.module.membership.dto.MembershipInvocationContext;
import br.com.rinos.app.api.module.membership.dto.MembershipMutationRequest;
import br.com.rinos.app.api.module.membership.facade.MembershipFacade;
import br.com.rinos.app.api.module.membership.vo.MembershipInvitationResult;
import br.com.rinos.app.api.module.membership.vo.MembershipMutationResult;

/** Deriva a UI/tenant exatos e entrega uma invocacao autenticada a facade de membership. */
@Component
@Lazy
public class SpringMembershipFacadeAdapter {

  private final WorkspaceAuthorizationContextAdapter workspaces;
  private final SpringAuthorizationAdapter authorization;
  private final MembershipFacade memberships;
  private final Clock clock;

  @Autowired
  public SpringMembershipFacadeAdapter(
      WorkspaceAuthorizationContextAdapter workspaces,
      SpringAuthorizationAdapter authorization,
      MembershipFacade memberships) {
    this(workspaces, authorization, memberships, Clock.systemUTC());
  }

  SpringMembershipFacadeAdapter(
      WorkspaceAuthorizationContextAdapter workspaces,
      SpringAuthorizationAdapter authorization,
      MembershipFacade memberships,
      Clock clock) {
    this.workspaces = workspaces;
    this.authorization = authorization;
    this.memberships = memberships;
    this.clock = clock;
  }

  public MembershipInvitationResult issue(UI ui, MembershipInvitationRequest request) {
    return memberships.issue(context(ui), request);
  }

  public MembershipInvitationResult resend(UI ui, UUID invitationPublicId) {
    return memberships.resend(context(ui), invitationPublicId);
  }

  public MembershipInvitationResult revoke(UI ui, UUID invitationPublicId) {
    return memberships.revoke(context(ui), invitationPublicId);
  }

  public MembershipMutationResult mutate(UI ui, MembershipMutationRequest request) {
    return memberships.mutate(context(ui), request);
  }

  private MembershipInvocationContext context(UI ui) {
    var workspace = workspaces.require(ui);
    var authenticated = authorization.current(workspace);
    Instant now = clock.instant();
    return new MembershipInvocationContext(
        authenticated.actor(), authenticated.membershipId(), authenticated.context(),
        authenticated.assurance(), "ui-membership-" + UUID.randomUUID(), now);
  }
}
