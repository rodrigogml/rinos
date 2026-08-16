package br.com.rinos.app.backend.module.plans.component;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.port.DefaultPlanAssignmentPort;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;
import br.com.rinos.app.api.module.plans.dto.TenantContractBootstrapRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.port.TenantContractBootstrapPort;

/** Compatibilidade do checkpoint de conta sobre o contrato tenant canônico. */
@Component
public class DefaultPlanAssignmentAdapter implements DefaultPlanAssignmentPort {

  private final TenantContractBootstrapPort contracts;

  public DefaultPlanAssignmentAdapter(TenantContractBootstrapPort contracts) {
    this.contracts = contracts;
  }

  @Autowired
  public DefaultPlanAssignmentAdapter(ObjectProvider<TenantContractBootstrapPort> contracts) {
    this.contracts = contracts.getIfAvailable();
  }

  @Override
  public AccountBootstrapResult assignDefaultPlan(AccountBootstrapRequest request) {
    if (contracts == null) {
      return AccountBootstrapResult.unavailable();
    }
    var result = contracts.ensure(new TenantContractBootstrapRequest(
        request.protocolId(), request.accountPublicId(), request.tenantPublicId(),
        request.founderUserId(), request.correlationId()));
    AccountBootstrapResultStatus status = switch (result.status()) {
      case COMPLETED -> AccountBootstrapResultStatus.ACCEPTED;
      case ALREADY_COMPLETED -> AccountBootstrapResultStatus.ALREADY_COMPLETED;
      case REJECTED -> AccountBootstrapResultStatus.REJECTED;
      case UNAVAILABLE -> AccountBootstrapResultStatus.UNAVAILABLE;
    };
    return new AccountBootstrapResult(
        status,
        result.contractPublicId() == null ? null : result.contractPublicId().toString(),
        result.safeReasonCode());
  }
}
