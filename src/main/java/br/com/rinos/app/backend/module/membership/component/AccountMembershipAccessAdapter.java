package br.com.rinos.app.backend.module.membership.component;
import org.springframework.stereotype.Component;
import br.com.rinos.app.api.module.membership.enums.MembershipStatus;
import br.com.rinos.app.backend.module.access.service.*;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import br.com.rinos.app.backend.module.account.repository.*;
import br.com.rinos.app.backend.module.membership.repository.AccountMembershipRepository;
@Component
@org.springframework.context.annotation.Lazy
public class AccountMembershipAccessAdapter implements AccountMembershipAccessPort {
 private final AccountMembershipRepository memberships; private final AccountRepository accounts; private final TenantRepository tenants;
 public AccountMembershipAccessAdapter(AccountMembershipRepository memberships,AccountRepository accounts,TenantRepository tenants){
  this.memberships=memberships;this.accounts=accounts;this.tenants=tenants;}
 public AccountMembershipAccessSnapshot inspect(long membershipId){
  if(membershipId<=0)return AccountMembershipAccessSnapshot.absent();
  return memberships.findById(membershipId).map(membership->accounts.findById(membership.getAccountId())
    .map(account->AccountMembershipAccessSnapshot.found(membership.getUserId(),account.getTenantId(),
      membership.getStatus()==MembershipStatus.ACTIVE,
      tenants.findById(account.getTenantId()).map(t->t.getStatus()==TenantStatus.OPERATIONAL).orElse(false)))
    .orElseGet(AccountMembershipAccessSnapshot::absent)).orElseGet(AccountMembershipAccessSnapshot::absent);
 }
}
