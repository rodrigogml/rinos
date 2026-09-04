package br.com.rinos.app.api.module.plans.port;

import br.com.rinos.app.api.module.plans.dto.PersonalContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;

/** Porta consumida pela ativação da identidade para assegurar PERSONAL/FREE. */
public interface PersonalContractBootstrapPort {

  ContractBootstrapResult ensure(PersonalContractBootstrapRequest request);
}
