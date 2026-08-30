package br.com.rinos.app.backend.module.storage.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;
import jakarta.persistence.LockModeType;

/** Acessa a fila de operações estruturais preservando a chave de idempotência de origem. */
public interface StorageOperationRepository extends JpaRepository<StorageOperationEntity, Long> {

  Optional<StorageOperationEntity> findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(
      Long tenantStorageRegistryId, StorageOperationType operationType, UUID idempotencyReference);

  /** Busca a próxima operação elegível sob lock, priorizando migrations já aceitas. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select operation from StorageOperationEntity operation
      where (operation.operationState = br.com.rinos.app.backend.module.storage.enums.StorageOperationState.QUEUED
        and (operation.nextAttemptAt is null or operation.nextAttemptAt <= :now))
        or (operation.operationState in (br.com.rinos.app.backend.module.storage.enums.StorageOperationState.CLAIMED,
              br.com.rinos.app.backend.module.storage.enums.StorageOperationState.RUNNING)
            and operation.leaseUntil <= :now)
      order by case when operation.operationType = br.com.rinos.app.backend.module.storage.enums.StorageOperationType.MIGRATE
                       then 0 else 1 end, operation.id
      """)
  List<StorageOperationEntity> findEligibleForUpdate(@Param("now") Instant now, Pageable pageable);
}
