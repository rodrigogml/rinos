package br.com.rinos.app.backend.module.plans.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.TenantContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;

class DefaultPlanAssignmentAdapterTest {

  @Test
  void shouldMapTenantContractToAccountCheckpoint() {
    TenantContractBootstrapPort contracts = mock(TenantContractBootstrapPort.class);
    UUID contractId = UUID.randomUUID();
    when(contracts.ensure(any())).thenReturn(new ContractBootstrapResult(
        ContractBootstrapStatus.COMPLETED, ContractScope.TENANT, contractId, null));
    DefaultPlanAssignmentAdapter adapter = new DefaultPlanAssignmentAdapter(contracts);

    var result = adapter.assignDefaultPlan(new AccountBootstrapRequest(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "account-plan-test"));

    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.ACCEPTED);
    assertThat(result.externalReference()).isEqualTo(contractId.toString());
    assertThat(result.safeReasonCode()).isNull();
  }
}
