package br.com.rinos.app.backend.module.account.component;
import org.springframework.stereotype.Component;
import br.com.rinos.app.api.module.account.port.AccountOperationalStatePort;
import br.com.rinos.app.api.module.account.vo.AccountOperationalSnapshot;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import br.com.rinos.app.backend.module.account.repository.*;
@Component
@org.springframework.context.annotation.Lazy
public class AccountOperationalStateAdapter implements AccountOperationalStatePort {
 private final AccountRepository accounts; private final TenantRepository tenants;
 public AccountOperationalStateAdapter(AccountRepository accounts,TenantRepository tenants){this.accounts=accounts;this.tenants=tenants;}
 public AccountOperationalSnapshot inspect(long tenantId){
  if(tenantId<=0)return new AccountOperationalSnapshot(true,false,null,null,null,false);
  return accounts.findByTenantId(tenantId).map(account->new AccountOperationalSnapshot(true,true,account.getId(),tenantId,account.getStatus(),
      tenants.findById(tenantId).map(t->t.getStatus()==TenantStatus.OPERATIONAL).orElse(false)))
    .orElseGet(()->new AccountOperationalSnapshot(true,false,null,null,null,false));
 }
}
