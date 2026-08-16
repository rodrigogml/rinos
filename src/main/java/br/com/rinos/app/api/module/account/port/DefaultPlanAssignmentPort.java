package br.com.rinos.app.api.module.account.port;

import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;

/** Atribui ou confirma exatamente um plano padrão vigente. */
public interface DefaultPlanAssignmentPort {
  AccountBootstrapResult assignDefaultPlan(AccountBootstrapRequest request);
}
