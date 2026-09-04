package br.com.rinos.app.api.module.account.port;

import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;

/** Solicita, sem assumir sucesso, o armazenamento isolado do tenant. */
public interface TenantProvisioningRequestPort {
  AccountBootstrapResult requestProvisioning(AccountBootstrapRequest request);
}
