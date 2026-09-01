package br.com.rinos.app.backend.module.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.enums.AccountPublicStage;
import br.com.rinos.app.api.module.account.enums.AccountStatus;
import br.com.rinos.app.api.module.account.port.DefaultPlanAssignmentPort;
import br.com.rinos.app.api.module.account.port.TenantAccessBootstrapPort;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;
import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;
import br.com.rinos.app.api.module.storage.port.TenantStorageReadinessPort;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;
import br.com.rinos.app.backend.module.account.entity.AccountAuditEventEntity;
import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.entity.AccountProvisioningCheckpointEntity;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.enums.AccountCreationIntentStatus;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import br.com.rinos.app.backend.module.account.repository.AccountAuditEventRepository;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountProvisioningCheckpointRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;

@DisplayName("Ativação final da criação de conta")
class AccountCreationActivationServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-01T15:00:00Z");

  @Test
  void activateNext_shouldPromoteAllStates_whenCheckpointsAndLiveGatesAreConfirmed() {
    Fixture fixture = new Fixture();
    fixture.stubReadyCandidate();
    when(fixture.storage.inspect(fixture.tenantPublicId)).thenReturn(readyStorage());
    when(fixture.access.bootstrapAccess(org.mockito.ArgumentMatchers.any())).thenReturn(completed("7"));
    when(fixture.plan.assignDefaultPlan(org.mockito.ArgumentMatchers.any())).thenReturn(completed("8"));

    boolean activated = fixture.service.activateNext();

    assertThat(activated).isTrue();
    assertThat(fixture.account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(fixture.tenant.getStatus()).isEqualTo(TenantStatus.OPERATIONAL);
    assertThat(fixture.intent.getStatus()).isEqualTo(AccountCreationIntentStatus.READY);
    assertThat(fixture.intent.getPublicStage()).isEqualTo(AccountPublicStage.AVAILABLE);
    verify(fixture.audit).save(org.mockito.ArgumentMatchers.any(AccountAuditEventEntity.class));
  }

  @Test
  void activateNext_shouldKeepCandidateNonOperational_whenOneCheckpointIsNotCompleted() {
    Fixture fixture = new Fixture();
    fixture.stubReadyCandidate();
    ReflectionTestUtils.setField(fixture.checkpoints.getLast(), "status",
        br.com.rinos.app.backend.module.account.enums.ProvisioningCheckpointStatus.PENDING);

    boolean activated = fixture.service.activateNext();

    assertThat(activated).isFalse();
    assertThat(fixture.account.getStatus()).isEqualTo(AccountStatus.CREATING);
    assertThat(fixture.tenant.getStatus()).isEqualTo(TenantStatus.RESERVED);
    assertThat(fixture.intent.getStatus()).isEqualTo(AccountCreationIntentStatus.ACCEPTED);
    verifyNoInteractions(fixture.storage, fixture.access, fixture.plan, fixture.audit);
  }

  @Test
  void activateNext_shouldKeepCandidateNonOperational_whenStorageReadinessIsLostBeforePromotion() {
    Fixture fixture = new Fixture();
    fixture.stubReadyCandidate();
    when(fixture.storage.inspect(fixture.tenantPublicId)).thenReturn(new TenantStorageReadinessSnapshotVO(
        true, true, false, TenantStorageAvailabilityEnum.MIGRATING, "TENANT_MIGRATING", NOW));

    boolean activated = fixture.service.activateNext();

    assertThat(activated).isFalse();
    assertThat(fixture.account.getStatus()).isEqualTo(AccountStatus.CREATING);
    assertThat(fixture.tenant.getStatus()).isEqualTo(TenantStatus.RESERVED);
    assertThat(fixture.intent.getPublicStage()).isEqualTo(AccountPublicStage.ACCEPTED);
    verify(fixture.access, never()).bootstrapAccess(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(fixture.plan, fixture.audit);
  }

  private static AccountBootstrapResult completed(String reference) {
    return new AccountBootstrapResult(AccountBootstrapResultStatus.ALREADY_COMPLETED, reference, null);
  }

  private static TenantStorageReadinessSnapshotVO readyStorage() {
    return new TenantStorageReadinessSnapshotVO(
        true, true, true, TenantStorageAvailabilityEnum.READY, null, NOW);
  }

  private static final class Fixture {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final AccountCreationIntentRepository intents = mock(AccountCreationIntentRepository.class);
    private final AccountProvisioningCheckpointRepository checkpointRepository = mock(
        AccountProvisioningCheckpointRepository.class);
    private final AccountAuditEventRepository audit = mock(AccountAuditEventRepository.class);
    private final TenantStorageReadinessPort storage = mock(TenantStorageReadinessPort.class);
    private final TenantAccessBootstrapPort access = mock(TenantAccessBootstrapPort.class);
    private final DefaultPlanAssignmentPort plan = mock(DefaultPlanAssignmentPort.class);
    private final UUID accountPublicId = UUID.randomUUID();
    private final UUID tenantPublicId = UUID.randomUUID();
    private final UUID protocolId = UUID.randomUUID();
    private final AccountEntity account = new AccountEntity(
        accountPublicId, 20L, 10L, "Conta", "BRL", "America/Sao_Paulo");
    private final TenantEntity tenant = new TenantEntity(tenantPublicId);
    private final AccountCreationIntentEntity intent = new AccountCreationIntentEntity(
        UUID.randomUUID(), protocolId, 10L, UUID.randomUUID(), new byte[32], 30L);
    private final List<AccountProvisioningCheckpointEntity> checkpoints = List.of(
        checkpoint(30L, ProvisioningStepType.STORAGE),
        checkpoint(30L, ProvisioningStepType.FOUNDING_MEMBERSHIP),
        checkpoint(30L, ProvisioningStepType.ACCESS_BOOTSTRAP),
        checkpoint(30L, ProvisioningStepType.DEFAULT_PLAN));
    private final AccountCreationActivationService service = new AccountCreationActivationService(
        accounts, tenants, intents, checkpointRepository, audit, storage, access, plan,
        Clock.fixed(NOW, ZoneOffset.UTC));

    private Fixture() {
      ReflectionTestUtils.setField(account, "id", 30L);
      ReflectionTestUtils.setField(tenant, "id", 20L);
    }

    private void stubReadyCandidate() {
      when(accounts.findNextReadyForActivationForUpdate()).thenReturn(List.of(account));
      when(tenants.findById(20L)).thenReturn(Optional.of(tenant));
      when(intents.findByAccountId(30L)).thenReturn(Optional.of(intent));
      when(checkpointRepository.findByAccountIdForUpdate(30L)).thenReturn(checkpoints);
    }

    private static AccountProvisioningCheckpointEntity checkpoint(
        long accountId,
        ProvisioningStepType stepType) {
      AccountProvisioningCheckpointEntity checkpoint = new AccountProvisioningCheckpointEntity(
          accountId, stepType);
      checkpoint.complete(stepType.name());
      return checkpoint;
    }
  }
}
