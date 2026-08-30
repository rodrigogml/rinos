package br.com.rinos.app.backend.module.storage.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.storage.repository.StorageOperationRepository;
import br.com.rinos.app.backend.module.storage.vo.StorageOperationClaimVO;
import br.com.rinos.app.config.StoragePropertiesConfig;

/**
 * Reclama atomicamente a próxima operação estrutural sem executar seu efeito físico.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
@Service
public class StorageOperationClaimService {
  private final StorageOperationRepository repository;
  private final StoragePropertiesConfig properties;
  private final Clock clock;

  /** Cria o serviço com relógio UTC da instância. */
  @Autowired
  public StorageOperationClaimService(StorageOperationRepository repository, StoragePropertiesConfig properties) {
    this(repository, properties, Clock.systemUTC());
  }

  /** Cria o serviço com relógio controlado para testes. */
  StorageOperationClaimService(StorageOperationRepository repository, StoragePropertiesConfig properties, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Reclama a próxima operação elegível em ordem FIFO, sob lock pessimista global.
   *
   * @param instanceId instância de manutenção comprovadamente eleita
   * @return posse criada ou vazio quando a fila não possui trabalho elegível
   */
  @Transactional
  public Optional<StorageOperationClaimVO> claimNext(String instanceId) {
    Objects.requireNonNull(instanceId, "instanceId must not be null");
    if (instanceId.isBlank()) {
      throw new IllegalArgumentException("instanceId must not be blank");
    }
    Instant now = clock.instant();
    return repository.findNextEligibleForUpdate(now).stream().findFirst().map(operation -> {
      Instant leaseUntil = now.plus(properties.operationLease());
      operation.claim(instanceId, leaseUntil);
      return new StorageOperationClaimVO(operation.getPublicId(), operation.getTenantStorageRegistryId(),
          operation.getOperationType(), leaseUntil);
    });
  }
}
