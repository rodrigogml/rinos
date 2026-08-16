package br.com.rinos.app.api.module.account.port;

import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;

/** Cria ou confirma a associação explícita do fundador. */
public interface FoundingMembershipBootstrapPort {
  AccountBootstrapResult bootstrapMembership(AccountBootstrapRequest request);
}
