package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;

class UserLifecyclePlanIntegrationTest {

  @Test
  void shouldRequirePersonalContractBeforeInitialActivation() {
    AuthSessionService sessions = mock(AuthSessionService.class);
    PersonalContractBootstrapPort contracts = mock(PersonalContractBootstrapPort.class);
    UserEntity user = pendingUser();
    UUID contractId = UUID.randomUUID();
    when(contracts.ensure(any())).thenReturn(new ContractBootstrapResult(
        ContractBootstrapStatus.COMPLETED, ContractScope.PERSONAL, contractId, null));
    UserLifecycleService service = new UserLifecycleService(sessions, contracts);

    service.transition(user, UserStatusEnum.ACTIVE, IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED", Instant.now(), UUID.randomUUID());

    verify(contracts).ensure(any());
    verify(user).setStatus(UserStatusEnum.ACTIVE);
  }

  @Test
  void shouldNotActivateWhenPersonalContractIsUnavailable() {
    PersonalContractBootstrapPort contracts = mock(PersonalContractBootstrapPort.class);
    UserEntity user = pendingUser();
    when(contracts.ensure(any())).thenReturn(new ContractBootstrapResult(
        ContractBootstrapStatus.UNAVAILABLE, ContractScope.PERSONAL, null,
        "PLAN_SOURCE_UNAVAILABLE"));
    UserLifecycleService service = new UserLifecycleService(
        mock(AuthSessionService.class), contracts);

    assertThatThrownBy(() -> service.transition(
        user, UserStatusEnum.ACTIVE, IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_VERIFIED", Instant.now(), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);

    verify(user, never()).setStatus(UserStatusEnum.ACTIVE);
  }

  private static UserEntity pendingUser() {
    UserEntity user = mock(UserEntity.class);
    when(user.getId()).thenReturn(42L);
    when(user.getStatus()).thenReturn(UserStatusEnum.PENDING_VERIFICATION);
    return user;
  }
}
