package br.com.rinos.app.backend.module.storage.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.facade.AuthorizationFacade;
import br.com.rinos.app.api.module.access.keys.InitialModuleAccessKeys;
import br.com.rinos.app.api.module.access.vo.AuthenticationAssurance;
import br.com.rinos.app.api.module.access.vo.AuthorizationActor;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;
import br.com.rinos.app.api.module.access.vo.HumanAuthorizationContext;
import br.com.rinos.app.api.module.storage.dto.TenantStorageAdministrationInvocationContext;
import br.com.rinos.app.api.module.storage.enums.TenantStorageDeactivationStatusEnum;
import br.com.rinos.app.api.module.storage.vo.TenantStorageDeactivationResultVO;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReconciliationSnapshotVO;
import br.com.rinos.app.backend.module.storage.service.TenantStorageDeactivationService;
import br.com.rinos.app.backend.module.storage.service.TenantStorageReconciliationAdministrationService;

class TenantStorageAdministrationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-30T22:00:00Z");

  @Test
  void reconcile_shouldRequireSensitiveCanonicalGlobalKeyBeforeInspection() {
    Fixture fixture = new Fixture();
    TenantStorageReconciliationSnapshotVO expected = new TenantStorageReconciliationSnapshotVO(
        List.of(), 0, NOW);
    when(fixture.reconciliation.reconcile(99L, "storage-admin-test", NOW)).thenReturn(expected);

    assertThat(fixture.facade.reconcile(context())).isSameAs(expected);

    ArgumentCaptor<AuthorizationRequest> request = ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(fixture.authorization).require(request.capture());
    assertThat(request.getValue().context()).isEqualTo(AuthorizationContext.global());
    assertThat(request.getValue().membershipId()).isNull();
    assertThat(request.getValue().requiredKeys())
        .containsExactly(InitialModuleAccessKeys.GLOBAL_PLATFORM_PROVISIONING_MANAGE);
    assertThat(request.getValue().sensitive()).isTrue();
  }

  @Test
  void requestDeactivation_shouldNotReachStorageWhenCanonicalAuthorizationDenies() {
    Fixture fixture = new Fixture();
    when(fixture.authorization.require(any())).thenThrow(new IllegalStateException("ACL_ASSURANCE_REQUIRED"));

    assertThatThrownBy(() -> fixture.facade.requestDeactivation(context(), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ACL_ASSURANCE_REQUIRED");

    verifyNoInteractions(fixture.reconciliation, fixture.deactivation);
  }

  @Test
  void requestDeactivation_shouldForwardOnlyTrustedActorAndPublicTargetAfterAuthorization() {
    Fixture fixture = new Fixture();
    UUID tenantPublicId = UUID.randomUUID();
    TenantStorageDeactivationResultVO expected = new TenantStorageDeactivationResultVO(
        TenantStorageDeactivationStatusEnum.DEACTIVATION_REQUESTED,
        "TENANT_STORAGE_DEACTIVATION_PENDING_GOVERNANCE", NOW);
    when(fixture.deactivation.requestDeactivation(tenantPublicId, 99L, "storage-admin-test", NOW))
        .thenReturn(expected);

    assertThat(fixture.facade.requestDeactivation(context(), tenantPublicId)).isSameAs(expected);

    verify(fixture.deactivation).requestDeactivation(tenantPublicId, 99L, "storage-admin-test", NOW);
  }

  private static TenantStorageAdministrationInvocationContext context() {
    return new TenantStorageAdministrationInvocationContext(new HumanAuthorizationContext(
        AuthorizationActor.human(99L), null, AuthorizationContext.global(),
        new AuthenticationAssurance(AuthenticationAssuranceEnum.MULTI_FACTOR,
            Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP),
            NOW.minusSeconds(60), NOW.minusSeconds(30))), "storage-admin-test", NOW);
  }

  private static final class Fixture {
    private final AuthorizationFacade authorization = mock(AuthorizationFacade.class);
    private final TenantStorageReconciliationAdministrationService reconciliation =
        mock(TenantStorageReconciliationAdministrationService.class);
    private final TenantStorageDeactivationService deactivation = mock(TenantStorageDeactivationService.class);
    private final TenantStorageAdministrationFacadeImpl facade = new TenantStorageAdministrationFacadeImpl(
        authorization, reconciliation, deactivation);
  }
}
