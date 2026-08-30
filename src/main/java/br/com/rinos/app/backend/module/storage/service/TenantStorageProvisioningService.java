package br.com.rinos.app.backend.module.storage.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
import br.com.rinos.app.api.module.account.port.TenantProvisioningRequestPort;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapRequest;
import br.com.rinos.app.api.module.account.vo.AccountBootstrapResult;
import br.com.rinos.app.backend.module.account.entity.AccountCreationIntentEntity;
import br.com.rinos.app.backend.module.account.entity.AccountEntity;
import br.com.rinos.app.backend.module.account.entity.TenantEntity;
import br.com.rinos.app.backend.module.account.repository.AccountCreationIntentRepository;
import br.com.rinos.app.backend.module.account.repository.AccountRepository;
import br.com.rinos.app.backend.module.account.repository.TenantRepository;
import br.com.rinos.app.backend.module.storage.component.TenantDatabaseCatalogService;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

/**
 * Reserva de forma atômica o registro global e a primeira operação de provisionamento de um tenant.
 *
 * <p>Esta fronteira não cria schema, não abre datasource de tenant e não concede prontidão. Ela usa somente o
 * catálogo global e devolve referências públicas da operação para que a saga de conta possa repetir a intenção com
 * segurança.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class TenantStorageProvisioningService implements TenantProvisioningRequestPort {

  private static final String CONTEXT_INVALID = "ACCOUNT_BOOTSTRAP_CONTEXT_INVALID";
  private static final String OPERATION_CONFLICT = "TENANT_STORAGE_OPERATION_CONFLICT";

  private final AccountRepository accountRepository;
  private final TenantRepository tenantRepository;
  private final AccountCreationIntentRepository intentRepository;
  private final TenantStorageRegistryRepository registryRepository;
  private final StorageOperationRepository operationRepository;
  private final StorageOperationStepRepository stepRepository;
  private final TenantDatabaseCatalogService catalogService;
  private final TransactionTemplate transactions;

  /**
   * Cria o adapter transacional do contrato de provisionamento de conta.
   *
   * @param accountRepository consulta da conta global
   * @param tenantRepository consulta do tenant global
   * @param intentRepository consulta da intenção e protocolo do cadastro
   * @param registryRepository persistência da reserva estrutural exclusiva
   * @param operationRepository persistência da fila estrutural
   * @param stepRepository persistência das etapas duráveis da operação
   * @param catalogService catálogo da versão estrutural esperada
   * @param transactionManager gerenciador da única transação global da reserva
   */
  public TenantStorageProvisioningService(AccountRepository accountRepository,
      TenantRepository tenantRepository, AccountCreationIntentRepository intentRepository,
      TenantStorageRegistryRepository registryRepository, StorageOperationRepository operationRepository,
      StorageOperationStepRepository stepRepository, TenantDatabaseCatalogService catalogService,
      PlatformTransactionManager transactionManager) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    this.tenantRepository = Objects.requireNonNull(tenantRepository, "tenantRepository must not be null");
    this.intentRepository = Objects.requireNonNull(intentRepository, "intentRepository must not be null");
    this.registryRepository = Objects.requireNonNull(registryRepository, "registryRepository must not be null");
    this.operationRepository = Objects.requireNonNull(operationRepository, "operationRepository must not be null");
    this.stepRepository = Objects.requireNonNull(stepRepository, "stepRepository must not be null");
    this.catalogService = Objects.requireNonNull(catalogService, "catalogService must not be null");
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
        "transactionManager must not be null"));
  }

  /**
   * Reserva a operação de provisionamento ou devolve a mesma referência em uma repetição válida.
   *
   * @param request protocolo e identidades públicas emitidos pelo cadastro de conta
   * @return resultado seguro da reserva; não indica criação física nem prontidão
   * @throws NullPointerException quando o pedido não for informado
   */
  @Override
  public AccountBootstrapResult requestProvisioning(AccountBootstrapRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    try {
      AccountBootstrapResult result = transactions.execute(status -> reserve(request));
      return Objects.requireNonNull(result, "reservation transaction must return a result");
    } catch (DataIntegrityViolationException exception) {
      return replayAfterCollision(request);
    } catch (RuntimeException exception) {
      return AccountBootstrapResult.unavailable();
    }
  }

  private AccountBootstrapResult reserve(AccountBootstrapRequest request) {
    ReservationContext context = resolveContext(request);
    if (context == null) {
      return rejected(CONTEXT_INVALID);
    }

    Optional<TenantStorageRegistryEntity> currentRegistry = registryRepository.findByTenantId(context.tenant().getId());
    if (currentRegistry.isPresent()) {
      return resultForExistingOperation(currentRegistry.get(), request.protocolId());
    }

    TenantDatabaseCatalogVO catalog = catalogService.inspect();
    TenantStorageRegistryEntity registry = registryRepository.saveAndFlush(new TenantStorageRegistryEntity(
        context.tenant().getId(), new TenantPhysicalIdentifier(newPhysicalIdentifier()),
        catalog.targetVersion().value()));
    StorageOperationEntity operation = operationRepository.saveAndFlush(new StorageOperationEntity(UUID.randomUUID(),
        registry.getId(), StorageOperationType.PROVISION, request.protocolId(), request.correlationId()));
    stepRepository.save(new StorageOperationStepEntity(operation.getId(), StorageOperationStepType.RESERVE));
    return accepted(operation);
  }

  private AccountBootstrapResult replayAfterCollision(AccountBootstrapRequest request) {
    ReservationContext context = resolveContext(request);
    if (context == null) {
      return rejected(CONTEXT_INVALID);
    }
    return registryRepository.findByTenantId(context.tenant().getId())
        .map(registry -> resultForExistingOperation(registry, request.protocolId()))
        .orElseGet(AccountBootstrapResult::unavailable);
  }

  private AccountBootstrapResult resultForExistingOperation(TenantStorageRegistryEntity registry, UUID protocolId) {
    return operationRepository.findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(
        registry.getId(), StorageOperationType.PROVISION, protocolId)
        .map(this::resultForExistingOperation)
        .orElseGet(() -> rejected(OPERATION_CONFLICT));
  }

  private AccountBootstrapResult resultForExistingOperation(StorageOperationEntity operation) {
    if (operation.getOperationState() == StorageOperationState.COMPLETED) {
      return new AccountBootstrapResult(AccountBootstrapResultStatus.ALREADY_COMPLETED,
          operation.getPublicId().toString(), null);
    }
    if (operation.getOperationState() == StorageOperationState.FAILED_FINAL
        || operation.getOperationState() == StorageOperationState.CANCELLED) {
      return rejected(operation.getSafeFailureCode() == null ? OPERATION_CONFLICT : operation.getSafeFailureCode());
    }
    return accepted(operation);
  }

  private ReservationContext resolveContext(AccountBootstrapRequest request) {
    AccountEntity account = accountRepository.findByPublicId(request.accountPublicId()).orElse(null);
    if (account == null || !Objects.equals(account.getFounderUserId(), request.founderUserId())) {
      return null;
    }
    TenantEntity tenant = tenantRepository.findById(account.getTenantId())
        .filter(candidate -> request.tenantPublicId().equals(candidate.getPublicId()))
        .orElse(null);
    AccountCreationIntentEntity intent = intentRepository.findByProtocolId(request.protocolId())
        .filter(candidate -> account.getId().equals(candidate.getAccountId()))
        .filter(candidate -> request.founderUserId() == candidate.getCreatorUserId())
        .orElse(null);
    if (tenant == null || intent == null) {
      return null;
    }
    return new ReservationContext(tenant);
  }

  private static AccountBootstrapResult accepted(StorageOperationEntity operation) {
    return new AccountBootstrapResult(AccountBootstrapResultStatus.ACCEPTED,
        operation.getPublicId().toString(), null);
  }

  private static AccountBootstrapResult rejected(String safeReasonCode) {
    return new AccountBootstrapResult(AccountBootstrapResultStatus.REJECTED, null, safeReasonCode);
  }

  private static String newPhysicalIdentifier() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private record ReservationContext(TenantEntity tenant) {
  }
}
