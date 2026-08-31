package br.com.rinos.app.backend.module.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.port.DefaultPlanAssignmentPort;
import br.com.rinos.app.api.module.account.port.FoundingMembershipBootstrapPort;
import br.com.rinos.app.api.module.account.port.TenantAccessBootstrapPort;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;
import br.com.rinos.app.api.module.storage.enums.TenantStorageAvailabilityEnum;
import br.com.rinos.app.api.module.storage.port.TenantStorageReadinessPort;
import br.com.rinos.app.api.module.storage.vo.TenantStorageReadinessSnapshotVO;
import br.com.rinos.app.backend.module.account.entity.AccountAuditEventEntity;
import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.entity.AccountProvisioningCheckpointEntity;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.enums.ProvisioningCheckpointStatus;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;
import br.com.rinos.app.backend.module.account.repository.AccountAuditEventRepository;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountProvisioningCheckpointRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;

/**
 * Avança uma única etapa elegível da saga de conta sem antecipar sua ativação.
 *
 * <p>A seleção pessimista preserva a ordem {@code STORAGE}, {@code FOUNDING_MEMBERSHIP},
 * {@code ACCESS_BOOTSTRAP} e {@code DEFAULT_PLAN}. O serviço somente usa contratos idempotentes
 * já publicados pelos módulos dependentes. Mesmo depois de todos os checkpoints completos, a
 * conta permanece em {@code CREATING}: a transição para ativa é responsabilidade exclusiva da
 * tarefa posterior de ativação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Service
public class AccountCreationSagaAdvanceService {

  private static final String SYSTEM_ORIGIN = "account-creation-saga";
  private static final String EVENT_COMPLETED = "ACCOUNT_PROVISIONING_CHECKPOINT_COMPLETED";
  private static final String EVENT_FAILED = "ACCOUNT_PROVISIONING_CHECKPOINT_FAILED";
  private static final String INVALID_CONTEXT = "ACCOUNT_PROVISIONING_CONTEXT_INVALID";
  private static final String DEPENDENCY_UNAVAILABLE = "ACCOUNT_DEPENDENCY_UNAVAILABLE";
  private static final String DEPENDENCY_REJECTED = "ACCOUNT_PROVISIONING_REJECTED";
  private static final String STORAGE_PENDING = "ACCOUNT_STORAGE_PENDING";
  private static final String STORAGE_UNAVAILABLE = "ACCOUNT_STORAGE_UNAVAILABLE";

  private final AccountRepository accounts;
  private final TenantRepository tenants;
  private final AccountCreationIntentRepository intents;
  private final AccountProvisioningCheckpointRepository checkpoints;
  private final AccountAuditEventRepository auditEvents;
  private final TenantStorageReadinessPort storageReadiness;
  private final FoundingMembershipBootstrapPort membershipBootstrap;
  private final TenantAccessBootstrapPort accessBootstrap;
  private final DefaultPlanAssignmentPort defaultPlanAssignment;
  private final AccountCreationPropertiesConfig properties;
  private final Clock clock;

  /**
   * Cria o coordenador com o relógio UTC usado pela aplicação em execução.
   *
   * @param accounts fonte canônica das contas em criação
   * @param tenants fonte canônica dos tenants reservados
   * @param intents protocolos aceitos do cadastro
   * @param checkpoints checkpoints duráveis da saga
   * @param auditEvents evidências sistêmicas minimizadas
   * @param storageReadiness gate público da prontidão de storage
   * @param membershipBootstrap etapa idempotente da associação fundadora
   * @param accessBootstrap etapa idempotente do grupo protegido e baseline
   * @param defaultPlanAssignment etapa idempotente do plano padrão
   * @param properties política fixa de retentativa
   */
  @Autowired
  public AccountCreationSagaAdvanceService(
      AccountRepository accounts,
      TenantRepository tenants,
      AccountCreationIntentRepository intents,
      AccountProvisioningCheckpointRepository checkpoints,
      AccountAuditEventRepository auditEvents,
      TenantStorageReadinessPort storageReadiness,
      FoundingMembershipBootstrapPort membershipBootstrap,
      TenantAccessBootstrapPort accessBootstrap,
      DefaultPlanAssignmentPort defaultPlanAssignment,
      AccountCreationPropertiesConfig properties) {
    this(accounts, tenants, intents, checkpoints, auditEvents, storageReadiness, membershipBootstrap,
        accessBootstrap, defaultPlanAssignment, properties, Clock.systemUTC());
  }

  /**
   * Cria o coordenador com relógio controlável para a validação do ciclo de retentativa.
   *
   * @param accounts fonte canônica das contas em criação
   * @param tenants fonte canônica dos tenants reservados
   * @param intents protocolos aceitos do cadastro
   * @param checkpoints checkpoints duráveis da saga
   * @param auditEvents evidências sistêmicas minimizadas
   * @param storageReadiness gate público da prontidão de storage
   * @param membershipBootstrap etapa idempotente da associação fundadora
   * @param accessBootstrap etapa idempotente do grupo protegido e baseline
   * @param defaultPlanAssignment etapa idempotente do plano padrão
   * @param properties política fixa de retentativa
   * @param clock relógio UTC da execução
   */
  AccountCreationSagaAdvanceService(
      AccountRepository accounts,
      TenantRepository tenants,
      AccountCreationIntentRepository intents,
      AccountProvisioningCheckpointRepository checkpoints,
      AccountAuditEventRepository auditEvents,
      TenantStorageReadinessPort storageReadiness,
      FoundingMembershipBootstrapPort membershipBootstrap,
      TenantAccessBootstrapPort accessBootstrap,
      DefaultPlanAssignmentPort defaultPlanAssignment,
      AccountCreationPropertiesConfig properties,
      Clock clock) {
    this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
    this.intents = Objects.requireNonNull(intents, "intents must not be null");
    this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints must not be null");
    this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents must not be null");
    this.storageReadiness = Objects.requireNonNull(storageReadiness,
        "storageReadiness must not be null");
    this.membershipBootstrap = Objects.requireNonNull(membershipBootstrap,
        "membershipBootstrap must not be null");
    this.accessBootstrap = Objects.requireNonNull(accessBootstrap, "accessBootstrap must not be null");
    this.defaultPlanAssignment = Objects.requireNonNull(defaultPlanAssignment,
        "defaultPlanAssignment must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Reclama e avança uma única conta elegível, sem trabalhar quando não existir etapa pronta.
   *
   * <p>O chamador deve executar este método sob a prova transacional da liderança de manutenção.
   *
   * @return {@code true} quando uma conta elegível foi observada; {@code false} quando a fila não
   *     continha trabalho elegível
   */
  @Transactional
  public boolean advanceNext() {
    Instant now = clock.instant();
    AccountEntity account = accounts.findNextCreatingWithEligibleCheckpointForUpdate(now).stream()
        .findFirst().orElse(null);
    if (account == null) {
      return false;
    }
    TenantEntity tenant = tenants.findById(account.getTenantId()).orElse(null);
    AccountCreationIntentEntity intent = intents.findByAccountId(account.getId()).orElse(null);
    Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointByStep = checkpointMap(
        checkpoints.findByAccountIdForUpdate(account.getId()));
    if (!hasValidContext(account, tenant, intent, checkpointByStep)) {
      failInvalidContext(account, tenant, intent, checkpointByStep, now);
      return true;
    }
    AccountBootstrapRequest request = new AccountBootstrapRequest(intent.getProtocolId(),
        account.getPublicId(), tenant.getPublicId(), account.getFounderUserId(),
        intent.getProtocolId().toString());
    advanceFirstEligible(account, tenant, intent, checkpointByStep, request, now);
    return true;
  }

  private void advanceFirstEligible(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointByStep,
      AccountBootstrapRequest request,
      Instant now) {
    AccountProvisioningCheckpointEntity storage = checkpointByStep.get(ProvisioningStepType.STORAGE);
    if (storage.getStatus() == ProvisioningCheckpointStatus.PROCESSING) {
      observeStorage(account, tenant, intent, storage, now);
      return;
    }
    if (storage.getStatus() != ProvisioningCheckpointStatus.COMPLETED) {
      return;
    }
    AccountProvisioningCheckpointEntity membership = checkpointByStep.get(
        ProvisioningStepType.FOUNDING_MEMBERSHIP);
    if (isEligible(membership)) {
      invokeBootstrap(account, tenant, intent, membership, request, now,
          membershipBootstrap::bootstrapMembership);
      return;
    }
    if (membership.getStatus() != ProvisioningCheckpointStatus.COMPLETED) {
      return;
    }
    AccountProvisioningCheckpointEntity access = checkpointByStep.get(ProvisioningStepType.ACCESS_BOOTSTRAP);
    if (isEligible(access)) {
      invokeBootstrap(account, tenant, intent, access, request, now, accessBootstrap::bootstrapAccess);
      return;
    }
    if (access.getStatus() != ProvisioningCheckpointStatus.COMPLETED) {
      return;
    }
    AccountProvisioningCheckpointEntity plan = checkpointByStep.get(ProvisioningStepType.DEFAULT_PLAN);
    if (isEligible(plan)) {
      invokeBootstrap(account, tenant, intent, plan, request, now,
          defaultPlanAssignment::assignDefaultPlan);
    }
  }

  private void observeStorage(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      AccountProvisioningCheckpointEntity checkpoint,
      Instant now) {
    TenantStorageReadinessSnapshotVO snapshot = inspectStorage(tenant.getPublicId());
    if (!snapshot.sourceAvailable()) {
      checkpoint.deferProcessing(now.plus(retryDelay(checkpoint.getAttemptCount())));
      return;
    }
    if (snapshot.ready()) {
      checkpoint.complete(null);
      audit(account, tenant, intent, EVENT_COMPLETED, ProvisioningStepType.STORAGE.name(), now);
      return;
    }
    if (!snapshot.tenantKnown() || snapshot.availability() == TenantStorageAvailabilityEnum.ATTENTION
        || snapshot.availability() == TenantStorageAvailabilityEnum.INACTIVE) {
      String safeCode = safeCode(snapshot.safeReasonCode(), STORAGE_UNAVAILABLE);
      checkpoint.fail(safeCode);
      audit(account, tenant, intent, EVENT_FAILED, safeCode, now);
      return;
    }
    checkpoint.deferProcessing(now.plus(retryDelay(checkpoint.getAttemptCount())));
  }

  private void invokeBootstrap(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      AccountProvisioningCheckpointEntity checkpoint,
      AccountBootstrapRequest request,
      Instant now,
      BootstrapInvocation invocation) {
    checkpoint.beginAttempt();
    AccountBootstrapResult result = invokeSafely(invocation, request);
    if (result.status() == AccountBootstrapResultStatus.ACCEPTED
        || result.status() == AccountBootstrapResultStatus.ALREADY_COMPLETED) {
      String reference = validReference(result.externalReference());
      if (reference != null) {
        checkpoint.complete(reference);
        audit(account, tenant, intent, EVENT_COMPLETED, checkpoint.getStepType().name(), now);
        return;
      }
      checkpoint.scheduleRetry(now.plus(retryDelay(checkpoint.getAttemptCount())), DEPENDENCY_UNAVAILABLE);
      return;
    }
    if (result.status() == AccountBootstrapResultStatus.REJECTED) {
      String safeCode = safeCode(result.safeReasonCode(), DEPENDENCY_REJECTED);
      checkpoint.fail(safeCode);
      audit(account, tenant, intent, EVENT_FAILED, safeCode, now);
      return;
    }
    checkpoint.scheduleRetry(now.plus(retryDelay(checkpoint.getAttemptCount())), DEPENDENCY_UNAVAILABLE);
  }

  private TenantStorageReadinessSnapshotVO inspectStorage(java.util.UUID tenantPublicId) {
    try {
      TenantStorageReadinessSnapshotVO snapshot = storageReadiness.inspect(tenantPublicId);
      if (snapshot != null) {
        return snapshot;
      }
    } catch (RuntimeException exception) {
    }
    return new TenantStorageReadinessSnapshotVO(false, false, false,
        TenantStorageAvailabilityEnum.ATTENTION, STORAGE_PENDING, clock.instant());
  }

  private static AccountBootstrapResult invokeSafely(
      BootstrapInvocation invocation,
      AccountBootstrapRequest request) {
    try {
      AccountBootstrapResult result = invocation.invoke(request);
      return result == null ? AccountBootstrapResult.unavailable() : result;
    } catch (RuntimeException exception) {
      return AccountBootstrapResult.unavailable();
    }
  }

  private static boolean hasValidContext(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointByStep) {
    return tenant != null
        && intent != null
        && Objects.equals(account.getTenantId(), tenant.getId())
        && Objects.equals(intent.getAccountId(), account.getId())
        && Objects.equals(intent.getCreatorUserId(), account.getFounderUserId())
        && checkpointByStep.size() == ProvisioningStepType.values().length;
  }

  private static Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointMap(
      List<AccountProvisioningCheckpointEntity> checkpointList) {
    Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> result = new EnumMap<>(
        ProvisioningStepType.class);
    for (AccountProvisioningCheckpointEntity checkpoint : checkpointList) {
      if (result.put(checkpoint.getStepType(), checkpoint) != null) {
        throw new IllegalStateException("duplicate account provisioning checkpoint");
      }
    }
    return result;
  }

  private void failInvalidContext(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointByStep,
      Instant now) {
    for (ProvisioningStepType step : ProvisioningStepType.values()) {
      AccountProvisioningCheckpointEntity checkpoint = checkpointByStep.get(step);
      if (checkpoint != null && checkpoint.getStatus() != ProvisioningCheckpointStatus.COMPLETED) {
        checkpoint.fail(INVALID_CONTEXT);
        break;
      }
    }
    if (tenant != null && intent != null) {
      audit(account, tenant, intent, EVENT_FAILED, INVALID_CONTEXT, now);
    }
  }

  private static boolean isEligible(AccountProvisioningCheckpointEntity checkpoint) {
    return checkpoint.getStatus() == ProvisioningCheckpointStatus.PENDING
        || checkpoint.getStatus() == ProvisioningCheckpointStatus.PROCESSING;
  }

  private Duration retryDelay(int attemptCount) {
    long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 30);
    Duration candidate;
    try {
      candidate = properties.outboxRetryBase().multipliedBy(multiplier);
    } catch (ArithmeticException exception) {
      return properties.outboxRetryMaximum();
    }
    return candidate.compareTo(properties.outboxRetryMaximum()) > 0
        ? properties.outboxRetryMaximum() : candidate;
  }

  private static String validReference(String value) {
    if (value == null || value.isBlank() || value.length() > 200) {
      return null;
    }
    return value.strip();
  }

  private static String safeCode(String value, String fallback) {
    if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,99}")) {
      return fallback;
    }
    return value;
  }

  private void audit(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      String eventType,
      String safeResultCode,
      Instant occurredAt) {
    auditEvents.save(new AccountAuditEventEntity(eventType, account.getId(), tenant.getId(),
        SYSTEM_ORIGIN, intent.getProtocolId().toString(), safeResultCode, occurredAt));
  }

  @FunctionalInterface
  private interface BootstrapInvocation {

    AccountBootstrapResult invoke(AccountBootstrapRequest request);
  }
}
