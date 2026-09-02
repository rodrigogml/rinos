package br.com.rinos.app.backend.module.account.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.enums.AccountStatus;
import br.com.rinos.app.api.module.account.port.DefaultPlanAssignmentPort;
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
import br.com.rinos.app.backend.module.account.enums.AccountCreationIntentStatus;
import br.com.rinos.app.backend.module.account.enums.ProvisioningCheckpointStatus;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;
import br.com.rinos.app.backend.module.account.enums.TenantStatus;
import br.com.rinos.app.backend.module.account.repository.AccountAuditEventRepository;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountProvisioningCheckpointRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;

/**
 * Promove uma conta somente depois da confirmação durável e atual dos quatro pré-requisitos.
 *
 * <p>O serviço não cria tenant, associação ou contrato. Ele revalida storage, baseline ACL e
 * plano por suas portas idempotentes e promove conta, tenant e protocolo na mesma transação
 * global. Qualquer dependência indisponível ou contexto que tenha mudado mantém a conta em
 * {@code CREATING}, para correção ou nova observação sem sucesso artificial.
 *
 * @author Rodrigo Leitão
 * @since 2026-09-01
 */
@Service
@ConditionalOnBean(DataSource.class)
public class AccountCreationActivationService {

  private static final String SYSTEM_ORIGIN = "account-creation-activation";
  private static final String EVENT_ACTIVATED = "ACCOUNT_ACTIVATED";
  private static final String ACTIVATED = "ACTIVATED";

  private final AccountRepository accounts;
  private final TenantRepository tenants;
  private final AccountCreationIntentRepository intents;
  private final AccountProvisioningCheckpointRepository checkpoints;
  private final AccountAuditEventRepository auditEvents;
  private final TenantStorageReadinessPort storageReadiness;
  private final TenantAccessBootstrapPort accessBootstrap;
  private final DefaultPlanAssignmentPort defaultPlanAssignment;
  private final Clock clock;

  /**
   * Cria o ativador usando o relógio UTC da aplicação.
   *
   * @param accounts fonte canônica das contas em criação
   * @param tenants fonte canônica dos tenants reservados
   * @param intents intenções idempotentes aceitas
   * @param checkpoints evidências duráveis de provisionamento
   * @param auditEvents evidências minimizadas da promoção final
   * @param storageReadiness gate público de prontidão do schema tenant
   * @param accessBootstrap revalidação idempotente da associação fundadora e baseline ACL
   * @param defaultPlanAssignment revalidação idempotente do contrato tenant padrão
   */
  @Autowired
  public AccountCreationActivationService(
      AccountRepository accounts,
      TenantRepository tenants,
      AccountCreationIntentRepository intents,
      AccountProvisioningCheckpointRepository checkpoints,
      AccountAuditEventRepository auditEvents,
      TenantStorageReadinessPort storageReadiness,
      TenantAccessBootstrapPort accessBootstrap,
      DefaultPlanAssignmentPort defaultPlanAssignment) {
    this(accounts, tenants, intents, checkpoints, auditEvents, storageReadiness, accessBootstrap,
        defaultPlanAssignment, Clock.systemUTC());
  }

  /**
   * Cria o ativador com relógio controlável para validações determinísticas.
   *
   * @param accounts fonte canônica das contas em criação
   * @param tenants fonte canônica dos tenants reservados
   * @param intents intenções idempotentes aceitas
   * @param checkpoints evidências duráveis de provisionamento
   * @param auditEvents evidências minimizadas da promoção final
   * @param storageReadiness gate público de prontidão do schema tenant
   * @param accessBootstrap revalidação idempotente da associação fundadora e baseline ACL
   * @param defaultPlanAssignment revalidação idempotente do contrato tenant padrão
   * @param clock relógio UTC da execução
   */
  AccountCreationActivationService(
      AccountRepository accounts,
      TenantRepository tenants,
      AccountCreationIntentRepository intents,
      AccountProvisioningCheckpointRepository checkpoints,
      AccountAuditEventRepository auditEvents,
      TenantStorageReadinessPort storageReadiness,
      TenantAccessBootstrapPort accessBootstrap,
      DefaultPlanAssignmentPort defaultPlanAssignment,
      Clock clock) {
    this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
    this.intents = Objects.requireNonNull(intents, "intents must not be null");
    this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints must not be null");
    this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents must not be null");
    this.storageReadiness = Objects.requireNonNull(storageReadiness,
        "storageReadiness must not be null");
    this.accessBootstrap = Objects.requireNonNull(accessBootstrap,
        "accessBootstrap must not be null");
    this.defaultPlanAssignment = Objects.requireNonNull(defaultPlanAssignment,
        "defaultPlanAssignment must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Ativa no máximo uma conta elegível, sem promover uma condição somente presumida no passado.
   *
   * <p>A seleção usa {@code FOR UPDATE SKIP LOCKED}; duas instâncias podem observar a fila, mas
   * somente uma mantém o lock necessário para concluir a promoção. Falhas e indisponibilidades
   * devolvem {@code false} e preservam todos os estados não operacionais.
   *
   * @return {@code true} somente quando uma conta, tenant e protocolo foram ativados
   */
  @Transactional
  public boolean activateNext() {
    AccountEntity account = accounts.findNextReadyForActivationForUpdate().stream()
        .findFirst().orElse(null);
    if (account == null) {
      return false;
    }
    TenantEntity tenant = tenants.findById(account.getTenantId()).orElse(null);
    AccountCreationIntentEntity intent = intents.findByAccountId(account.getId()).orElse(null);
    Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointByStep = checkpointMap(
        checkpoints.findByAccountIdForUpdate(account.getId()));
    if (!hasActivatableContext(account, tenant, intent, checkpointByStep)) {
      return false;
    }
    AccountBootstrapRequest request = new AccountBootstrapRequest(intent.getProtocolId(),
        account.getPublicId(), tenant.getPublicId(), account.getFounderUserId(),
        intent.getProtocolId().toString());
    if (!isStorageReady(tenant.getPublicId())
        || !isConfirmed(accessBootstrap.bootstrapAccess(request))
        || !isConfirmed(defaultPlanAssignment.assignDefaultPlan(request))) {
      return false;
    }
    account.activate();
    tenant.makeOperational();
    intent.markReady();
    auditEvents.save(new AccountAuditEventEntity(EVENT_ACTIVATED, account.getId(), tenant.getId(),
        SYSTEM_ORIGIN, intent.getProtocolId().toString(), ACTIVATED, clock.instant()));
    return true;
  }

  private boolean isStorageReady(java.util.UUID tenantPublicId) {
    try {
      TenantStorageReadinessSnapshotVO snapshot = storageReadiness.inspect(tenantPublicId);
      return snapshot != null && snapshot.sourceAvailable() && snapshot.tenantKnown()
          && snapshot.ready() && snapshot.availability() == TenantStorageAvailabilityEnum.READY;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private static boolean isConfirmed(AccountBootstrapResult result) {
    return result != null
        && (result.status() == AccountBootstrapResultStatus.ACCEPTED
            || result.status() == AccountBootstrapResultStatus.ALREADY_COMPLETED)
        && result.externalReference() != null
        && !result.externalReference().isBlank()
        && result.externalReference().length() <= 200;
  }

  private static boolean hasActivatableContext(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      Map<ProvisioningStepType, AccountProvisioningCheckpointEntity> checkpointByStep) {
    return account.getStatus() == AccountStatus.CREATING
        && tenant != null
        && tenant.getStatus() == TenantStatus.RESERVED
        && intent != null
        && (intent.getStatus() == AccountCreationIntentStatus.ACCEPTED
            || intent.getStatus() == AccountCreationIntentStatus.PROCESSING)
        && Objects.equals(account.getTenantId(), tenant.getId())
        && Objects.equals(intent.getAccountId(), account.getId())
        && Objects.equals(intent.getCreatorUserId(), account.getFounderUserId())
        && checkpointByStep.size() == ProvisioningStepType.values().length
        && checkpointByStep.values().stream().allMatch(
            checkpoint -> checkpoint.getStatus() == ProvisioningCheckpointStatus.COMPLETED);
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
}
