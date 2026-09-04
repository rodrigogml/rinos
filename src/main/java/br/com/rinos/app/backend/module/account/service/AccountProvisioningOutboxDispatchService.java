package br.com.rinos.app.backend.module.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.port.TenantProvisioningRequestPort;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;
import br.com.rinos.app.backend.module.account.entity.AccountAuditEventEntity;
import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.entity.AccountOutboxEventEntity;
import br.com.rinos.app.backend.module.account.entity.AccountProvisioningCheckpointEntity;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.enums.ProvisioningStepType;
import br.com.rinos.app.backend.module.account.repository.AccountAuditEventRepository;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountOutboxEventRepository;
import br.com.rinos.app.backend.module.account.repository.AccountProvisioningCheckpointRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.account.vo.AccountProvisioningOutboxClaimVO;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;

/**
 * Despacha a outbox da criação para o contrato de storage sem acoplar as demais etapas da saga.
 *
 * <p>A chamada ao port ocorre fora da transação do lease. Antes e depois dela, o serviço relê o
 * evento sob lock, de forma que uma queda, uma resposta perdida ou uma troca de instância apenas
 * produz nova chamada idempotente pelo mesmo protocolo. Esta etapa jamais altera os estados de
 * conta ou tenant nem marca o checkpoint como concluído: prontidão física ainda depende do gate
 * de storage e os outros três checkpoints permanecem de responsabilidade de fases posteriores.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-31
 */
@Service
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "rfw.database.update", name = "enabled", havingValue = "true")
public class AccountProvisioningOutboxDispatchService {

  private static final String SYSTEM_ORIGIN = "account-provisioning-outbox";
  private static final String EVENT_ACCEPTED = "ACCOUNT_STORAGE_PROVISIONING_ACCEPTED";
  private static final String EVENT_REJECTED = "ACCOUNT_STORAGE_PROVISIONING_REJECTED";
  private static final String INVALID_CONTEXT = "ACCOUNT_PROVISIONING_CONTEXT_INVALID";
  private static final String DEPENDENCY_UNAVAILABLE = "ACCOUNT_DEPENDENCY_UNAVAILABLE";
  private static final String DEPENDENCY_REJECTED = "ACCOUNT_PROVISIONING_REJECTED";

  private final AccountOutboxEventRepository outboxRepository;
  private final AccountRepository accountRepository;
  private final TenantRepository tenantRepository;
  private final AccountCreationIntentRepository intentRepository;
  private final AccountProvisioningCheckpointRepository checkpointRepository;
  private final AccountAuditEventRepository auditRepository;
  private final TenantProvisioningRequestPort provisioningPort;
  private final AccountCreationPropertiesConfig properties;
  private final TransactionTemplate transactions;
  private final Clock clock;

  /**
   * Cria o despachante com o relógio UTC de produção.
   *
   * @param outboxRepository fila persistida da criação
   * @param accountRepository fonte canônica da conta
   * @param tenantRepository fonte canônica do tenant
   * @param intentRepository fonte canônica do protocolo aceito
   * @param checkpointRepository etapas da saga
   * @param auditRepository auditoria minimizada do resultado sistêmico
   * @param provisioningPort fronteira pública do provisionamento de storage
   * @param properties limites fixos de lease e repetição
   * @param transactionManager gerenciador da transação global
   */
  @Autowired
  public AccountProvisioningOutboxDispatchService(
      AccountOutboxEventRepository outboxRepository,
      AccountRepository accountRepository,
      TenantRepository tenantRepository,
      AccountCreationIntentRepository intentRepository,
      AccountProvisioningCheckpointRepository checkpointRepository,
      AccountAuditEventRepository auditRepository,
      TenantProvisioningRequestPort provisioningPort,
      AccountCreationPropertiesConfig properties,
      PlatformTransactionManager transactionManager) {
    this(outboxRepository, accountRepository, tenantRepository, intentRepository, checkpointRepository,
        auditRepository, provisioningPort, properties, transactionManager, Clock.systemUTC());
  }

  /**
   * Cria o despachante com relógio controlável pelos testes do módulo.
   *
   * @param outboxRepository fila persistida da criação
   * @param accountRepository fonte canônica da conta
   * @param tenantRepository fonte canônica do tenant
   * @param intentRepository fonte canônica do protocolo aceito
   * @param checkpointRepository etapas da saga
   * @param auditRepository auditoria minimizada do resultado sistêmico
   * @param provisioningPort fronteira pública do provisionamento de storage
   * @param properties limites fixos de lease e repetição
   * @param transactionManager gerenciador da transação global
   * @param clock relógio UTC para leases e retentativas
   */
  AccountProvisioningOutboxDispatchService(
      AccountOutboxEventRepository outboxRepository,
      AccountRepository accountRepository,
      TenantRepository tenantRepository,
      AccountCreationIntentRepository intentRepository,
      AccountProvisioningCheckpointRepository checkpointRepository,
      AccountAuditEventRepository auditRepository,
      TenantProvisioningRequestPort provisioningPort,
      AccountCreationPropertiesConfig properties,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository must not be null");
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
    this.intentRepository = Objects.requireNonNull(intentRepository, "intentRepository must not be null");
    this.checkpointRepository = Objects.requireNonNull(checkpointRepository,
        "checkpointRepository must not be null");
    this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository must not be null");
    this.provisioningPort = Objects.requireNonNull(provisioningPort, "provisioningPort must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
        "transactionManager must not be null"));
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Reclama a primeira intenção elegível sob lock pessimista e lease exclusivo.
   *
   * @param instanceId identificador da instância de manutenção comprovadamente eleita
   * @return lease criado ou vazio quando não existir trabalho elegível
   */
  @Transactional
  public Optional<AccountProvisioningOutboxClaimVO> claimNext(String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId must not be blank");
    }
    Instant now = clock.instant();
    return outboxRepository.findNextEligibleForUpdate(now).stream().findFirst().map(event -> {
      Instant leaseUntil = now.plus(properties.outboxLease());
      event.claim(instanceId.strip(), leaseUntil);
      return new AccountProvisioningOutboxClaimVO(event.getEventId(), instanceId, leaseUntil);
    });
  }

  /**
   * Entrega uma intenção reclamada ao storage e confirma somente o resultado correspondente.
   *
   * @param claim posse durável previamente concedida à instância atual
   */
  public void dispatch(AccountProvisioningOutboxClaimVO claim) {
    Objects.requireNonNull(claim, "claim must not be null");
    DispatchRequest request = transactions.execute(status -> prepare(claim));
    if (request == null) {
      return;
    }
    AccountBootstrapResult result = requestProvisioning(request.request());
    transactions.executeWithoutResult(status -> confirm(claim, request, result));
  }

  private DispatchRequest prepare(AccountProvisioningOutboxClaimVO claim) {
    Instant now = clock.instant();
    AccountOutboxEventEntity event = outboxRepository.findByEventIdForUpdate(claim.eventId()).orElse(null);
    if (event == null || !event.hasActiveLease(claim.leaseOwner(), now)) {
      return null;
    }
    AccountEntity account = accountRepository.findById(event.getAggregateId()).orElse(null);
    AccountCreationIntentEntity intent = account == null
        ? null : intentRepository.findByAccountId(account.getId()).orElse(null);
    TenantEntity tenant = account == null
        ? null : tenantRepository.findById(account.getTenantId()).orElse(null);
    AccountProvisioningCheckpointEntity checkpoint = account == null
        ? null : checkpointRepository.findByAccountIdAndStepTypeForUpdate(
            account.getId(), ProvisioningStepType.STORAGE).orElse(null);
    if (!isValidContext(event, account, tenant, intent, checkpoint)) {
      event.fail();
      if (checkpoint != null) {
        checkpoint.fail(INVALID_CONTEXT);
      }
      saveAudit(account, tenant, intent, EVENT_REJECTED, INVALID_CONTEXT, now);
      return null;
    }
    checkpoint.beginAttempt();
    AccountBootstrapRequest request = new AccountBootstrapRequest(
        intent.getProtocolId(), account.getPublicId(), tenant.getPublicId(), account.getFounderUserId(),
        intent.getProtocolId().toString());
    return new DispatchRequest(account, tenant, intent, request);
  }

  private void confirm(
      AccountProvisioningOutboxClaimVO claim,
      DispatchRequest request,
      AccountBootstrapResult result) {
    Instant now = clock.instant();
    AccountOutboxEventEntity event = outboxRepository.findByEventIdForUpdate(claim.eventId()).orElse(null);
    if (event == null || !event.hasActiveLease(claim.leaseOwner(), now)) {
      return;
    }
    AccountProvisioningCheckpointEntity checkpoint = checkpointRepository
        .findByAccountIdAndStepTypeForUpdate(request.account().getId(), ProvisioningStepType.STORAGE)
        .orElse(null);
    if (checkpoint == null) {
      event.fail();
      saveAudit(request.account(), request.tenant(), request.intent(), EVENT_REJECTED, INVALID_CONTEXT, now);
      return;
    }
    if (result.status() == AccountBootstrapResultStatus.ACCEPTED
        || result.status() == AccountBootstrapResultStatus.ALREADY_COMPLETED) {
      String reference = validReference(result.externalReference());
      if (reference == null) {
        scheduleRetry(event, checkpoint, now);
        return;
      }
      checkpoint.markProcessing(reference);
      event.publish(now);
      saveAudit(request.account(), request.tenant(), request.intent(), EVENT_ACCEPTED,
          result.status().name(), now);
      return;
    }
    if (result.status() == AccountBootstrapResultStatus.REJECTED) {
      String safeCode = safeCode(result.safeReasonCode(), DEPENDENCY_REJECTED);
      checkpoint.fail(safeCode);
      event.fail();
      saveAudit(request.account(), request.tenant(), request.intent(), EVENT_REJECTED, safeCode, now);
      return;
    }
    scheduleRetry(event, checkpoint, now);
  }

  private AccountBootstrapResult requestProvisioning(AccountBootstrapRequest request) {
    try {
      AccountBootstrapResult result = provisioningPort.requestProvisioning(request);
      return result == null ? AccountBootstrapResult.unavailable() : result;
    } catch (RuntimeException exception) {
      return AccountBootstrapResult.unavailable();
    }
  }

  private void scheduleRetry(
      AccountOutboxEventEntity event,
      AccountProvisioningCheckpointEntity checkpoint,
      Instant now) {
    Instant nextAttempt = now.plus(retryDelay(event.getAttemptCount()));
    checkpoint.scheduleRetry(nextAttempt, DEPENDENCY_UNAVAILABLE);
    event.scheduleRetry(nextAttempt);
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

  private static boolean isValidContext(
      AccountOutboxEventEntity event,
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      AccountProvisioningCheckpointEntity checkpoint) {
    return AccountOutboxEventEntity.PROVISIONING_REQUESTED_EVENT_TYPE.equals(event.getEventType())
        && "ACCOUNT".equals(event.getAggregateType())
        && account != null
        && tenant != null
        && intent != null
        && checkpoint != null
        && Objects.equals(intent.getAccountId(), account.getId())
        && Objects.equals(account.getTenantId(), tenant.getId())
        && Objects.equals(intent.getCreatorUserId(), account.getFounderUserId());
  }

  private static String validReference(String reference) {
    if (reference == null || reference.isBlank() || reference.length() > 200) {
      return null;
    }
    return reference.strip();
  }

  private static String safeCode(String value, String fallback) {
    if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,99}")) {
      return fallback;
    }
    return value;
  }

  private void saveAudit(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      String eventType,
      String safeResultCode,
      Instant occurredAt) {
    if (account == null || tenant == null || intent == null) {
      return;
    }
    auditRepository.save(new AccountAuditEventEntity(eventType, account.getId(), tenant.getId(),
        SYSTEM_ORIGIN, intent.getProtocolId().toString(), safeResultCode, occurredAt));
  }

  private record DispatchRequest(
      AccountEntity account,
      TenantEntity tenant,
      AccountCreationIntentEntity intent,
      AccountBootstrapRequest request) {
  }
}
