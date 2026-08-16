package br.com.rinos.app.backend.module.membership.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import br.com.rinos.app.api.module.account.enums.AccountStatus;
import br.com.rinos.app.api.module.membership.enums.*;
import br.com.rinos.app.api.module.membership.vo.MembershipInvitationResult;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import br.com.rinos.app.backend.module.account.repository.*;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.service.*;
import br.com.rinos.app.backend.module.identity.vo.ProtectedAuthenticationKeyVO;
import br.com.rinos.app.backend.module.membership.entity.*;
import br.com.rinos.app.backend.module.membership.repository.*;
import br.com.rinos.app.config.MembershipInvitationPropertiesConfig;

/** Persistência interna chamada somente depois dos gates de autorização e garantia. */
@Service
@org.springframework.context.annotation.Lazy
public class MembershipInvitationService {
  private static final String PROOF_DOMAIN = "membership/invitation-proof/v1";
  private final AccountMembershipRepository memberships; private final MembershipInvitationRepository invitations;
  private final MembershipEventRepository events; private final MembershipOutboxEventRepository outbox;
  private final AccountRepository accounts; private final TenantRepository tenants; private final UserRepository users;
  private final EmailNormalizationService emails; private final VerificationTokenService tokens;
  private final AuthenticationKeyringMacService macs; private final AuthenticationKeyringService keyring;
  private final MembershipPlanCapacityPort plans;
  private final MembershipInvitationRateLimitService rateLimits; private final MembershipInvitationPropertiesConfig properties;
  private final TransactionTemplate transactions;
  public MembershipInvitationService(AccountMembershipRepository memberships,MembershipInvitationRepository invitations,
      MembershipEventRepository events,MembershipOutboxEventRepository outbox,AccountRepository accounts,TenantRepository tenants,
      UserRepository users,EmailNormalizationService emails,VerificationTokenService tokens,AuthenticationKeyringMacService macs,
      AuthenticationKeyringService keyring,MembershipPlanCapacityPort plans,MembershipInvitationRateLimitService rateLimits,MembershipInvitationPropertiesConfig properties,
      PlatformTransactionManager transactionManager){this.memberships=memberships;this.invitations=invitations;
    this.events=events;this.outbox=outbox;this.accounts=accounts;this.tenants=tenants;this.users=users;this.emails=emails;
    this.tokens=tokens;this.macs=macs;this.keyring=keyring;this.plans=plans;this.rateLimits=rateLimits;this.properties=properties;this.transactions=new TransactionTemplate(transactionManager);}

  public MembershipInvitationResult issue(long inviterMembershipId,UUID accountPublicId,String email,
      MembershipRoleType role,String canonicalOrigin,String correlationId,Instant occurredAt){
    validateContext(inviterMembershipId,accountPublicId,role,canonicalOrigin,correlationId,occurredAt);
    var normalized=emails.normalize(email).normalizedEmail();
    var context=inviterContext(inviterMembershipId,accountPublicId);
    if(context==null)return rejected("MEMBERSHIP_INVITATION_CONTEXT_INVALID");
    var target=users.findByNormalizedEmail(normalized).orElse(null);
    if(target!=null&&memberships.findByAccountIdAndUserIdAndCurrentMarker(context.accountId(),target.getId(),1).isPresent())
      return rejected("MEMBERSHIP_ALREADY_CURRENT");
    var pending=pending(context.accountId(),normalized,occurredAt);
    if(pending!=null)return new MembershipInvitationResult(MembershipInvitationResultStatus.ALREADY_PENDING,
      pending.getPublicId(),null,pending.getExpiresAt(),null);
    try{return transactions.execute(status->{var result=createInvitation(context,normalized,role,canonicalOrigin,correlationId,occurredAt);
      if(result.status()==MembershipInvitationResultStatus.RATE_LIMITED)status.setRollbackOnly();return result;});}
    catch(DataIntegrityViolationException collision){
      var winner=pending(context.accountId(),normalized,occurredAt);
      if(winner!=null)return new MembershipInvitationResult(MembershipInvitationResultStatus.ALREADY_PENDING,
        winner.getPublicId(),null,winner.getExpiresAt(),null);
      throw collision;
    }
  }

  @Transactional
  public MembershipInvitationResult decide(long userId,UUID invitationPublicId,String proof,boolean accept,
      String correlationId,Instant occurredAt){
    if(userId<=0||invitationPublicId==null||proof==null||proof.isBlank()||correlationId==null||correlationId.isBlank()
        ||correlationId.length()>100||occurredAt==null)throw new IllegalArgumentException("invitation decision context is invalid");
    var invitation=invitations.findByPublicIdForUpdate(invitationPublicId).orElse(null);
    if(invitation==null||invitation.getStatus()!=MembershipInvitationStatus.PENDING)return rejected("MEMBERSHIP_INVITATION_INVALID");
    if(!occurredAt.isBefore(invitation.getExpiresAt())){invitation.expire();outbox.cancelInvitationDelivery(invitation.getId());invitations.saveAndFlush(invitation);return rejected("MEMBERSHIP_INVITATION_EXPIRED");}
    var user=users.findById(userId).filter(u->u.getStatus()==UserStatusEnum.ACTIVE
      &&u.getNormalizedEmail().equals(invitation.getNormalizedEmail())).orElse(null);
    var account=accounts.findById(invitation.getAccountId()).filter(a->a.getStatus()==AccountStatus.ACTIVE).orElse(null);
    if(user==null||account==null||tenants.findById(account.getTenantId()).filter(t->t.getStatus()==TenantStatus.OPERATIONAL).isEmpty()
        ||!matches(invitation,proof))return rejected("MEMBERSHIP_INVITATION_INVALID");
    if(!accept){invitation.decline(userId,occurredAt);outbox.cancelInvitationDelivery(invitation.getId());invitations.save(invitation);
      auditAndPublish(invitation,null,userId,"INVITATION_DECLINED",correlationId,occurredAt);
      return new MembershipInvitationResult(MembershipInvitationResultStatus.DECLINED,invitationPublicId,null,null,null);}
    var plan=plans.evaluate(account.getId(),userId);
    if(!plan.sourceAvailable())return unavailable("MEMBERSHIP_PLAN_UNAVAILABLE");
    if(!plan.allowed())return rejected("MEMBERSHIP_PLAN_LIMIT_REACHED");
    if(memberships.findByAccountIdAndUserIdAndCurrentMarker(account.getId(),userId,1).isPresent())
      return rejected("MEMBERSHIP_ALREADY_CURRENT");
    var membership=memberships.saveAndFlush(new AccountMembershipEntity(UUID.randomUUID(),account.getId(),userId,
      invitation.getProposedRoleType(),MembershipOriginType.INVITATION,occurredAt));
    invitation.accept(userId,occurredAt);outbox.cancelInvitationDelivery(invitation.getId());invitations.save(invitation);
    auditAndPublish(invitation,membership,userId,"INVITATION_ACCEPTED",correlationId,occurredAt);
    return new MembershipInvitationResult(MembershipInvitationResultStatus.ACCEPTED,invitationPublicId,null,null,null);
  }

  @Transactional
  public MembershipInvitationResult resend(long inviterMembershipId,UUID invitationPublicId,
      String canonicalOrigin,String correlationId,Instant occurredAt){
    validateAdministrativeInvitationContext(inviterMembershipId,invitationPublicId,correlationId,occurredAt);
    if(canonicalOrigin==null||canonicalOrigin.isBlank()||canonicalOrigin.length()>64)
      throw new IllegalArgumentException("invitation origin is invalid");
    var invitation=invitations.findByPublicIdForUpdate(invitationPublicId).orElse(null);
    if(invitation==null||invitation.getStatus()!=MembershipInvitationStatus.PENDING
        ||!occurredAt.isBefore(invitation.getExpiresAt())||!sameActiveInviterAccount(inviterMembershipId,invitation.getAccountId()))
      return rejected("MEMBERSHIP_INVITATION_INVALID");
    invitation.supersede();outbox.cancelInvitationDelivery(invitation.getId());invitations.saveAndFlush(invitation);
    auditAndPublish(invitation,null,memberships.findById(inviterMembershipId).orElseThrow().getUserId(),
      "INVITATION_SUPERSEDED",correlationId,occurredAt);
    var context=memberships.findById(inviterMembershipId).map(m->new InviterContext(m.getId(),m.getUserId(),m.getAccountId())).orElseThrow();
    var replacement=createInvitation(context,invitation.getNormalizedEmail(),invitation.getProposedRoleType(),canonicalOrigin,correlationId,occurredAt);
    if(replacement.status()==MembershipInvitationResultStatus.RATE_LIMITED){TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();return replacement;}
    return new MembershipInvitationResult(MembershipInvitationResultStatus.REISSUED,replacement.invitationPublicId(),
      replacement.transientProof(),replacement.expiresAt(),replacement.safeReasonCode());
  }

  @Transactional
  public MembershipInvitationResult revoke(long inviterMembershipId,UUID invitationPublicId,
      String correlationId,Instant occurredAt){
    validateAdministrativeInvitationContext(inviterMembershipId,invitationPublicId,correlationId,occurredAt);
    var invitation=invitations.findByPublicIdForUpdate(invitationPublicId).orElse(null);
    if(invitation==null||invitation.getStatus()!=MembershipInvitationStatus.PENDING
        ||!sameActiveInviterAccount(inviterMembershipId,invitation.getAccountId()))return rejected("MEMBERSHIP_INVITATION_INVALID");
    invitation.revoke();outbox.cancelInvitationDelivery(invitation.getId());invitations.save(invitation);long actor=memberships.findById(inviterMembershipId).orElseThrow().getUserId();
    auditAndPublish(invitation,null,actor,"INVITATION_REVOKED",correlationId,occurredAt);
    return new MembershipInvitationResult(MembershipInvitationResultStatus.REVOKED,invitationPublicId,null,null,null);
  }

  private MembershipInvitationResult createInvitation(InviterContext context,String email,MembershipRoleType role,String canonicalOrigin,
      String correlationId,Instant occurredAt){
    var existing=pending(context.accountId(),email,occurredAt);if(existing!=null)return new MembershipInvitationResult(
      MembershipInvitationResultStatus.ALREADY_PENDING,existing.getPublicId(),null,existing.getExpiresAt(),null);
    var rate=rateLimits.reserve(context.accountId(),context.membershipId(),email,canonicalOrigin);
    if(!rate.allowed()){return new MembershipInvitationResult(
      MembershipInvitationResultStatus.RATE_LIMITED,null,null,null,"MEMBERSHIP_INVITATION_RATE_LIMITED");}
    UUID publicId=UUID.randomUUID();String proof=tokens.generate();
    var protectedProof=macs.protect(PROOF_DOMAIN,proofInput(publicId,proof));
    var invitation=invitations.saveAndFlush(new MembershipInvitationEntity(publicId,context.accountId(),context.membershipId(),email,
      role,protectedProof.digest(),protectedProof.keyVersion(),occurredAt.plus(properties.validity())));
    auditAndPublishIssued(invitation,context.userId(),correlationId,occurredAt,proof);
    return new MembershipInvitationResult(MembershipInvitationResultStatus.ISSUED,publicId,proof,invitation.getExpiresAt(),null);
  }
  private void auditAndPublish(MembershipInvitationEntity invitation,AccountMembershipEntity membership,long actor,
      String eventType,String correlationId,Instant occurredAt){
    events.save(new MembershipEventEntity(eventType,invitation.getAccountId(),membership==null?null:membership.getId(),
      invitation.getId(),actor,correlationId,"COMPLETED",occurredAt));
    outbox.saveAndFlush(new MembershipOutboxEventEntity(UUID.randomUUID(),"INVITATION",invitation.getId(),eventType,
      "{\"invitationPublicId\":\""+invitation.getPublicId()+"\"}"));
  }
  private void auditAndPublishIssued(MembershipInvitationEntity invitation,long actor,String correlationId,
      Instant occurredAt,String proof){
    events.save(new MembershipEventEntity("INVITATION_ISSUED",invitation.getAccountId(),null,
      invitation.getId(),actor,correlationId,"COMPLETED",occurredAt));
    byte[] plaintext=proof.getBytes(StandardCharsets.UTF_8);
    try{var encrypted=keyring.encrypt(MembershipInvitationDeliveryService.SECRET_DOMAIN,plaintext);
      outbox.saveAndFlush(new MembershipOutboxEventEntity(UUID.randomUUID(),invitation.getId(),"INVITATION_ISSUED",
        "{\"invitationPublicId\":\""+invitation.getPublicId()+"\"}",encrypted,invitation.getExpiresAt()));}
    finally{Arrays.fill(plaintext,(byte)0);}
  }
  private MembershipInvitationEntity pending(Long accountId,String email,Instant at){
    var value=invitations.findByAccountIdAndNormalizedEmailAndPendingMarker(accountId,email,1).orElse(null);
    if(value!=null&&!at.isBefore(value.getExpiresAt())){transactions.executeWithoutResult(s->{
      invitations.findByPublicIdForUpdate(value.getPublicId()).filter(i->i.getStatus()==MembershipInvitationStatus.PENDING)
        .ifPresent(i->{i.expire();outbox.cancelInvitationDelivery(i.getId());invitations.saveAndFlush(i);});});return null;}return value;
  }
  private InviterContext inviterContext(long membershipId,UUID accountPublicId){
    var membership=memberships.findById(membershipId).filter(m->m.getStatus()==MembershipStatus.ACTIVE).orElse(null);
    if(membership==null)return null;var account=accounts.findById(membership.getAccountId())
      .filter(a->a.getPublicId().equals(accountPublicId)&&a.getStatus()==AccountStatus.ACTIVE).orElse(null);
    if(account==null||tenants.findById(account.getTenantId()).filter(t->t.getStatus()==TenantStatus.OPERATIONAL).isEmpty())return null;
    return new InviterContext(membership.getId(),membership.getUserId(),account.getId());
  }
  private boolean sameActiveInviterAccount(long membershipId,long accountId){var membership=memberships.findById(membershipId)
    .filter(m->m.getStatus()==MembershipStatus.ACTIVE&&m.getAccountId()==accountId).orElse(null);
    if(membership==null)return false;var account=accounts.findById(accountId).filter(a->a.getStatus()==AccountStatus.ACTIVE).orElse(null);
    return account!=null&&tenants.findById(account.getTenantId()).filter(t->t.getStatus()==TenantStatus.OPERATIONAL).isPresent();}
  private boolean matches(MembershipInvitationEntity invitation,String proof){return macs.matches(PROOF_DOMAIN,
    proofInput(invitation.getPublicId(),proof),new ProtectedAuthenticationKeyVO(invitation.getProofDigest(),invitation.getProofKeyId()));}
  private static byte[] proofInput(UUID id,String proof){return (id+":"+proof).getBytes(StandardCharsets.UTF_8);}
  private static void validateContext(long membershipId,UUID accountId,MembershipRoleType role,String origin,String correlation,Instant at){
    if(membershipId<=0||accountId==null||role==null||origin==null||origin.isBlank()||origin.length()>64
        ||correlation==null||correlation.isBlank()||correlation.length()>100||at==null)
      throw new IllegalArgumentException("invitation issue context is invalid");}
  private static void validateAdministrativeInvitationContext(long membershipId,UUID invitationId,String correlation,Instant at){
    if(membershipId<=0||invitationId==null||correlation==null||correlation.isBlank()||correlation.length()>100||at==null)
      throw new IllegalArgumentException("invitation administration context is invalid");}
  private static MembershipInvitationResult rejected(String reason){return new MembershipInvitationResult(
    MembershipInvitationResultStatus.REJECTED,null,null,null,reason);}
  private static MembershipInvitationResult unavailable(String reason){return new MembershipInvitationResult(
    MembershipInvitationResultStatus.UNAVAILABLE,null,null,null,reason);}
  private record InviterContext(long membershipId,long userId,long accountId){}
}
