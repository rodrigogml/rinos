package br.com.rinos.app.backend.module.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import br.com.rinos.app.api.module.account.enums.AccountBootstrapResultStatus;
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
import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationState;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.repository.StorageOperationStepRepository;
import br.com.rinos.app.backend.module.storage.repository.TenantStorageRegistryRepository;
import br.com.rinos.app.backend.module.storage.vo.TenantDatabaseCatalogVO;
import br.eng.rodrigogml.rfw.database.vo.DatabaseVersionVO;

@DisplayName("Reserva idempotente de armazenamento de tenant")
class TenantStorageProvisioningServiceTest {

  private AccountRepository accountRepository;
  private TenantRepository tenantRepository;
  private AccountCreationIntentRepository intentRepository;
  private TenantStorageRegistryRepository registryRepository;
  private StorageOperationRepository operationRepository;
  private StorageOperationStepRepository stepRepository;
  private TenantDatabaseCatalogService catalogService;
  private TenantStorageProvisioningService service;
  private AccountBootstrapRequest request;
  private AccountEntity account;
  private TenantEntity tenant;
  private AccountCreationIntentEntity intent;

  @BeforeEach
  void setUp() {
    accountRepository = mock(AccountRepository.class);
    tenantRepository = mock(TenantRepository.class);
    intentRepository = mock(AccountCreationIntentRepository.class);
    registryRepository = mock(TenantStorageRegistryRepository.class);
    operationRepository = mock(StorageOperationRepository.class);
    stepRepository = mock(StorageOperationStepRepository.class);
    catalogService = mock(TenantDatabaseCatalogService.class);
    service = new TenantStorageProvisioningService(accountRepository, tenantRepository, intentRepository,
        registryRepository, operationRepository, stepRepository, catalogService, new NoOpTransactionManager());

    request = new AccountBootstrapRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 11L,
        "storage-provisioning-test");
    account = mock(AccountEntity.class);
    tenant = mock(TenantEntity.class);
    intent = mock(AccountCreationIntentEntity.class);
    when(account.getId()).thenReturn(21L);
    when(account.getTenantId()).thenReturn(31L);
    when(account.getFounderUserId()).thenReturn(11L);
    when(tenant.getId()).thenReturn(31L);
    when(tenant.getPublicId()).thenReturn(request.tenantPublicId());
    when(intent.getAccountId()).thenReturn(21L);
    when(intent.getCreatorUserId()).thenReturn(11L);
    when(accountRepository.findByPublicId(request.accountPublicId())).thenReturn(Optional.of(account));
    when(tenantRepository.findById(31L)).thenReturn(Optional.of(tenant));
    when(intentRepository.findByProtocolId(request.protocolId())).thenReturn(Optional.of(intent));
  }

  @Test
  void requestProvisioning_shouldReserveRegistryOperationAndFirstStep_whenRequestIsValid() {
    TenantStorageRegistryEntity persistedRegistry = mock(TenantStorageRegistryEntity.class);
    StorageOperationEntity persistedOperation = mock(StorageOperationEntity.class);
    UUID operationPublicId = UUID.randomUUID();
    when(registryRepository.findByTenantId(31L)).thenReturn(Optional.empty());
    when(catalogService.inspect()).thenReturn(catalog("20260829001"));
    when(registryRepository.saveAndFlush(any(TenantStorageRegistryEntity.class))).thenReturn(persistedRegistry);
    when(persistedRegistry.getId()).thenReturn(41L);
    when(operationRepository.saveAndFlush(any(StorageOperationEntity.class))).thenReturn(persistedOperation);
    when(persistedOperation.getId()).thenReturn(51L);
    when(persistedOperation.getPublicId()).thenReturn(operationPublicId);

    AccountBootstrapResult result = service.requestProvisioning(request);

    ArgumentCaptor<TenantStorageRegistryEntity> registryCaptor = ArgumentCaptor.forClass(
        TenantStorageRegistryEntity.class);
    ArgumentCaptor<StorageOperationEntity> operationCaptor = ArgumentCaptor.forClass(StorageOperationEntity.class);
    verify(registryRepository).saveAndFlush(registryCaptor.capture());
    verify(operationRepository).saveAndFlush(operationCaptor.capture());
    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.ACCEPTED);
    assertThat(result.externalReference()).isEqualTo(operationPublicId.toString());
    assertThat(registryCaptor.getValue().getTenantId()).isEqualTo(31L);
    assertThat(registryCaptor.getValue().getExpectedVersion()).isEqualTo("20260829001");
    assertThat(registryCaptor.getValue().getPhysicalIdentifier().value()).matches("[a-f0-9]{32}");
    assertThat(operationCaptor.getValue().getTenantStorageRegistryId()).isEqualTo(41L);
    assertThat(operationCaptor.getValue().getOperationType()).isEqualTo(StorageOperationType.PROVISION);
    assertThat(operationCaptor.getValue().getIdempotencyReference()).isEqualTo(request.protocolId());
    assertThat(operationCaptor.getValue().getCorrelationId()).isEqualTo(request.correlationId());
    verify(stepRepository).save(any());
  }

  @Test
  void requestProvisioning_shouldReturnExistingReference_whenSameProtocolIsAlreadyQueued() {
    TenantStorageRegistryEntity registry = mock(TenantStorageRegistryEntity.class);
    StorageOperationEntity operation = mock(StorageOperationEntity.class);
    UUID operationPublicId = UUID.randomUUID();
    when(registry.getId()).thenReturn(41L);
    when(registryRepository.findByTenantId(31L)).thenReturn(Optional.of(registry));
    when(operationRepository.findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(41L,
        StorageOperationType.PROVISION, request.protocolId())).thenReturn(Optional.of(operation));
    when(operation.getOperationState()).thenReturn(StorageOperationState.QUEUED);
    when(operation.getPublicId()).thenReturn(operationPublicId);

    AccountBootstrapResult result = service.requestProvisioning(request);

    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.ACCEPTED);
    assertThat(result.externalReference()).isEqualTo(operationPublicId.toString());
    verify(registryRepository, never()).saveAndFlush(any());
    verify(operationRepository, never()).saveAndFlush(any());
  }

  @Test
  void requestProvisioning_shouldReportCompletedReplay_whenSameProtocolFinishedEarlier() {
    TenantStorageRegistryEntity registry = mock(TenantStorageRegistryEntity.class);
    StorageOperationEntity operation = mock(StorageOperationEntity.class);
    UUID operationPublicId = UUID.randomUUID();
    when(registry.getId()).thenReturn(41L);
    when(registryRepository.findByTenantId(31L)).thenReturn(Optional.of(registry));
    when(operationRepository.findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(41L,
        StorageOperationType.PROVISION, request.protocolId())).thenReturn(Optional.of(operation));
    when(operation.getOperationState()).thenReturn(StorageOperationState.COMPLETED);
    when(operation.getPublicId()).thenReturn(operationPublicId);

    AccountBootstrapResult result = service.requestProvisioning(request);

    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.ALREADY_COMPLETED);
    assertThat(result.externalReference()).isEqualTo(operationPublicId.toString());
  }

  @Test
  void requestProvisioning_shouldConvergeToExistingReference_whenConcurrentReservationCollides() {
    TenantStorageRegistryEntity registry = mock(TenantStorageRegistryEntity.class);
    StorageOperationEntity operation = mock(StorageOperationEntity.class);
    UUID operationPublicId = UUID.randomUUID();
    when(registryRepository.findByTenantId(31L)).thenReturn(Optional.empty(), Optional.of(registry));
    when(catalogService.inspect()).thenReturn(catalog("20260829001"));
    when(registryRepository.saveAndFlush(any(TenantStorageRegistryEntity.class)))
        .thenThrow(new DataIntegrityViolationException("unique tenant registry"));
    when(registry.getId()).thenReturn(41L);
    when(operationRepository.findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(41L,
        StorageOperationType.PROVISION, request.protocolId())).thenReturn(Optional.of(operation));
    when(operation.getOperationState()).thenReturn(StorageOperationState.QUEUED);
    when(operation.getPublicId()).thenReturn(operationPublicId);

    AccountBootstrapResult result = service.requestProvisioning(request);

    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.ACCEPTED);
    assertThat(result.externalReference()).isEqualTo(operationPublicId.toString());
    verify(operationRepository, never()).saveAndFlush(any());
  }

  @Test
  void requestProvisioning_shouldRejectWithoutReservation_whenAccountContextDoesNotMatchRequest() {
    when(account.getFounderUserId()).thenReturn(12L);

    AccountBootstrapResult result = service.requestProvisioning(request);

    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.REJECTED);
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_BOOTSTRAP_CONTEXT_INVALID");
    verify(registryRepository, never()).saveAndFlush(any());
    verify(operationRepository, never()).saveAndFlush(any());
  }

  @Test
  void requestProvisioning_shouldFailSafely_whenTenantCatalogCannotBeRead() {
    when(registryRepository.findByTenantId(31L)).thenReturn(Optional.empty());
    when(catalogService.inspect()).thenThrow(new IllegalStateException("catalog unavailable"));

    AccountBootstrapResult result = service.requestProvisioning(request);

    assertThat(result.status()).isEqualTo(AccountBootstrapResultStatus.UNAVAILABLE);
    assertThat(result.safeReasonCode()).isEqualTo("ACCOUNT_DEPENDENCY_UNAVAILABLE");
    verify(registryRepository, never()).saveAndFlush(any());
  }

  private static TenantDatabaseCatalogVO catalog(String version) {
    return mock(TenantDatabaseCatalogVO.class, invocation -> {
      if (invocation.getMethod().getName().equals("targetVersion")) {
        return new DatabaseVersionVO(version);
      }
      if (invocation.getMethod().getName().equals("scripts")) {
        return List.of();
      }
      return invocation.callRealMethod();
    });
  }

  private static final class NoOpTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
    }

    @Override
    public void rollback(TransactionStatus status) {
    }
  }
}
