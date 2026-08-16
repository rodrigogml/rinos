package br.com.rinos.app.backend.module.access.component;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessPort;
import br.com.rinos.app.backend.module.access.service.AccountMembershipAccessSnapshot;

/** Adapter fail-safe removido automaticamente quando membership publicar a implementação real. */
@Component
@ConditionalOnMissingBean(AccountMembershipAccessPort.class)
public class UnavailableAccountMembershipAccessAdapter implements AccountMembershipAccessPort {

  @Override
  public AccountMembershipAccessSnapshot inspect(long membershipId) {
    return AccountMembershipAccessSnapshot.unavailable();
  }
}
