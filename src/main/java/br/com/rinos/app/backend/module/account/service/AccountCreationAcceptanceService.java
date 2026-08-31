package br.com.rinos.app.backend.module.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.api.module.account.dto.AccountCreationRequest;
import br.com.rinos.app.api.module.account.enums.AccountCreationResultStatus;
import br.com.rinos.app.api.module.account.vo.AccountCreationResult;
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
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;

/**
 * Aceita uma intenção idempotente depois que a fronteira validou identidade, sessão e prova.
 *
 * <p>A reserva de origem, os registros globais da conta, a intenção, a auditoria e a outbox
 * participam da mesma transação. Um replay nunca reserva nova cota.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-15
 */
@Service
@Lazy
public class AccountCreationAcceptanceService {

  private final TenantRepository tenants;
  private final AccountRepository accounts;
  private final AccountCreationIntentRepository intents;
  private final AccountProvisioningCheckpointRepository checkpoints;
  private final AccountOutboxEventRepository outbox;
  private final AccountAuditEventRepository audits;
  private final AccountCreationAdmissionService admissionService;
  private final TransactionTemplate transactions;

  /**
   * Cria o serviço de aceite transacional.
   *
   * @param tenants persistência global de tenants
   * @param accounts persistência global das contas
   * @param intents persistência de idempotência
   * @param checkpoints persistência das etapas futuras
   * @param outbox persistência da solicitação assíncrona
   * @param audits persistência de auditoria minimizada
   * @param admissionService reserva e bloqueio por origem
   * @param transactionManager fronteira transacional global
   */
  public AccountCreationAcceptanceService(
      TenantRepository tenants,
      AccountRepository accounts,
      AccountCreationIntentRepository intents,
      AccountProvisioningCheckpointRepository checkpoints,
      AccountOutboxEventRepository outbox,
      AccountAuditEventRepository audits,
      AccountCreationAdmissionService admissionService,
      PlatformTransactionManager transactionManager) {
    this.tenants = Objects.requireNonNull(tenants, "tenants must not be null");
    this.accounts = Objects.requireNonNull(accounts, "accounts must not be null");
    this.intents = Objects.requireNonNull(intents, "intents must not be null");
    this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints must not be null");
    this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
    this.audits = Objects.requireNonNull(audits, "audits must not be null");
    this.admissionService = Objects.requireNonNull(admissionService, "admissionService must not be null");
    transactions = new TransactionTemplate(
        Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
  }

  /**
   * Procura uma intenção já aceita sem executar prova, contador ou novos efeitos persistentes.
   *
   * @param creatorUserId identidade autenticada que iniciou a intenção
   * @param request dados normalizados do pedido
   * @return replay, conflito ou {@code null} quando a intenção ainda não existe
   */
  public AccountCreationResult findExisting(long creatorUserId, AccountCreationRequest request) {
    if (creatorUserId <= 0 || request == null) {
      throw new IllegalArgumentException("account creation lookup is invalid");
    }
    return replay(creatorUserId, request.idempotencyKey(), hash(request));
  }

  /**
   * Aceita uma intenção nova ou retorna seu replay sem criar uma segunda conta.
   *
   * @param creatorUserId identidade autenticada que iniciou a intenção
   * @param request dados normalizados e explicitamente confirmados
   * @param correlationId correlação técnica sem segredo
   * @param occurredAt instante UTC comum da decisão
   * @param origin origem binária resolvida pela borda confiável
   * @param humanVerificationValid {@code true} somente após a validação remota da prova apresentada
   * @return aceite, replay, conflito ou negação segura
   */
  public AccountCreationResult accept(
      long creatorUserId,
      AccountCreationRequest request,
      String correlationId,
      Instant occurredAt,
      OriginAddressVO origin,
      boolean humanVerificationValid) {
    if (creatorUserId <= 0 || request == null || correlationId == null || correlationId.isBlank()
        || occurredAt == null || origin == null) {
      throw new IllegalArgumentException("trusted account creation context is incomplete");
    }
    byte[] payloadHash = hash(request);
    AccountCreationResult existing = replay(creatorUserId, request.idempotencyKey(), payloadHash);
    if (existing != null) {
      return existing;
    }
    try {
      return transactions.execute(status -> create(
          creatorUserId,
          request,
          payloadHash,
          correlationId,
          occurredAt,
          origin,
          humanVerificationValid));
    } catch (DataIntegrityViolationException collision) {
      AccountCreationResult winner = replay(creatorUserId, request.idempotencyKey(), payloadHash);
      if (winner != null) {
        return winner;
      }
      throw collision;
    }
  }

  private AccountCreationResult create(
      long creatorUserId,
      AccountCreationRequest request,
      byte[] payloadHash,
      String correlationId,
      Instant occurredAt,
      OriginAddressVO origin,
      boolean humanVerificationValid) {
    AccountCreationResult existing = replay(creatorUserId, request.idempotencyKey(), payloadHash);
    if (existing != null) {
      return existing;
    }
    AccountCreationAdmissionResult admission = admissionService.admit(
        origin, humanVerificationValid, occurredAt);
    if (!admission.admitted()) {
      return new AccountCreationResult(
          AccountCreationResultStatus.REJECTED,
          null,
          null,
          null,
          admission.safeReasonCode(),
          admission.retryAfter());
    }
    TenantEntity tenant = tenants.saveAndFlush(new TenantEntity(UUID.randomUUID()));
    AccountEntity account = accounts.saveAndFlush(new AccountEntity(
        UUID.randomUUID(),
        tenant.getId(),
        creatorUserId,
        request.displayName(),
        request.baseCurrency(),
        request.timeZoneId()));
    for (ProvisioningStepType step : ProvisioningStepType.values()) {
      checkpoints.save(new AccountProvisioningCheckpointEntity(account.getId(), step));
    }
    UUID protocol = UUID.randomUUID();
    AccountCreationIntentEntity intent = intents.saveAndFlush(new AccountCreationIntentEntity(
        UUID.randomUUID(),
        protocol,
        creatorUserId,
        request.idempotencyKey(),
        payloadHash,
        account.getId()));
    audits.save(new AccountAuditEventEntity(
        "ACCOUNT_CREATION_ACCEPTED",
        account.getId(),
        tenant.getId(),
        creatorUserId,
        correlationId,
        "ACCEPTED",
        occurredAt));
    outbox.saveAndFlush(new AccountOutboxEventEntity(
        UUID.randomUUID(), account.getId(),
        AccountOutboxEventEntity.PROVISIONING_REQUESTED_EVENT_TYPE, "{}"));
    return new AccountCreationResult(
        AccountCreationResultStatus.ACCEPTED,
        protocol,
        account.getPublicId(),
        intent.getPublicStage(),
        null,
        null);
  }

  private AccountCreationResult replay(long creatorUserId, UUID key, byte[] payloadHash) {
    return intents.findByCreatorUserIdAndIdempotencyKey(creatorUserId, key).map(intent -> {
      if (!Arrays.equals(payloadHash, intent.getPayloadHash())) {
        return new AccountCreationResult(
            AccountCreationResultStatus.CONFLICT,
            null,
            null,
            null,
            "ACCOUNT_IDEMPOTENCY_CONFLICT",
            null);
      }
      AccountEntity account = accounts.findById(intent.getAccountId()).orElseThrow();
      return new AccountCreationResult(
          AccountCreationResultStatus.REPLAYED,
          intent.getProtocolId(),
          account.getPublicId(),
          intent.getPublicStage(),
          null,
          null);
    }).orElse(null);
  }

  private static byte[] hash(AccountCreationRequest request) {
    try {
      String value = request.displayName() + "\u001f" + request.baseCurrency() + "\u001f"
          + request.timeZoneId();
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
