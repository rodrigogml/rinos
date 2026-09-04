package br.com.rinos.app.backend.module.membership.component;
import java.time.Instant; import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException; import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager; import org.springframework.transaction.support.TransactionTemplate;
import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus; import br.com.rinos.app.api.module.account.port.FoundingMembershipBootstrapPort;
import br.com.rinos.app.api.module.account.vo.*; import br.com.rinos.app.api.module.membership.enums.*;
import br.com.rinos.app.backend.module.account.repository.*; import br.com.rinos.app.backend.module.membership.entity.*; import br.com.rinos.app.backend.module.membership.repository.*;
@Component
@org.springframework.context.annotation.Lazy
public class FoundingMembershipBootstrapAdapter implements FoundingMembershipBootstrapPort {
 private final AccountRepository accounts; private final TenantRepository tenants; private final AccountCreationIntentRepository intents;
 private final AccountMembershipRepository memberships; private final MembershipEventRepository events; private final MembershipOutboxEventRepository outbox;
 private final TransactionTemplate transactions;
 public FoundingMembershipBootstrapAdapter(AccountRepository accounts,TenantRepository tenants,AccountCreationIntentRepository intents,
   AccountMembershipRepository memberships,MembershipEventRepository events,MembershipOutboxEventRepository outbox,
   PlatformTransactionManager transactionManager){this.accounts=accounts;this.tenants=tenants;
   this.intents=intents;this.memberships=memberships;this.events=events;this.outbox=outbox;this.transactions=new TransactionTemplate(transactionManager);}
 @Override
 public AccountBootstrapResult bootstrapMembership(AccountBootstrapRequest request){
  var account=accounts.findByPublicId(request.accountPublicId()).orElse(null);
  if(account==null||account.getFounderUserId()!=request.founderUserId())return rejected();
  var tenant=tenants.findById(account.getTenantId()).filter(t->t.getPublicId().equals(request.tenantPublicId())).orElse(null);
  var intent=intents.findByProtocolId(request.protocolId()).filter(i->i.getAccountId().equals(account.getId())).orElse(null);
  if(tenant==null||intent==null)return rejected();
  var current=memberships.findByAccountIdAndUserIdAndCurrentMarker(account.getId(),request.founderUserId(),1).orElse(null);
  if(current!=null)return completed(current);
  try{
   return transactions.execute(status->create(account.getId(),request));
  }catch(DataIntegrityViolationException collision){
   return memberships.findByAccountIdAndUserIdAndCurrentMarker(account.getId(),request.founderUserId(),1).map(this::completed).orElseThrow(()->collision);
  }
 }
 private AccountBootstrapResult create(Long accountId,AccountBootstrapRequest request){
   var current=memberships.findByAccountIdAndUserIdAndCurrentMarker(accountId,request.founderUserId(),1).orElse(null);
   if(current!=null)return completed(current);
   var membership=memberships.saveAndFlush(new AccountMembershipEntity(UUID.randomUUID(),accountId,request.founderUserId(),
     MembershipRoleType.ACCOUNT_ADMINISTRATOR,MembershipOriginType.FOUNDER,Instant.now()));
   events.save(new MembershipEventEntity("FOUNDING_MEMBERSHIP_CREATED",accountId,membership.getId(),"account-registration",
     request.correlationId(),"COMPLETED",Instant.now()));
   outbox.saveAndFlush(new MembershipOutboxEventEntity(UUID.randomUUID(),membership.getId(),"FOUNDING_MEMBERSHIP_CREATED",
     "{\"membershipId\":"+membership.getId()+"}"));
   return new AccountBootstrapResult(AccountBootstrapResultStatus.ACCEPTED,membership.getPublicId().toString(),null);
 }
 private AccountBootstrapResult completed(AccountMembershipEntity membership){return new AccountBootstrapResult(
   AccountBootstrapResultStatus.ALREADY_COMPLETED,membership.getPublicId().toString(),null);}
 private static AccountBootstrapResult rejected(){return new AccountBootstrapResult(AccountBootstrapResultStatus.REJECTED,null,"ACCOUNT_BOOTSTRAP_CONTEXT_INVALID");}
}
