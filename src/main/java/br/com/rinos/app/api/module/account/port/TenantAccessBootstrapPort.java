package br.com.rinos.app.api.module.account.port;

import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;

/** Cria ou confirma o grupo protegido e sua baseline explícita de chaves. */
public interface TenantAccessBootstrapPort {
  AccountBootstrapResult bootstrapAccess(AccountBootstrapRequest request);
}
