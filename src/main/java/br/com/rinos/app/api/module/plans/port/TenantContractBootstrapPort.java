package br.com.rinos.app.api.module.plans.port;

import br.com.rinos.app.api.module.plans.dto.TenantContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;

/** Porta consumida pelo provisionamento para assegurar TENANT/FREE e o fundador. */
public interface TenantContractBootstrapPort {

  ContractBootstrapResult ensure(TenantContractBootstrapRequest request);
}
