package br.com.rinos.app.backend.module.storage.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;

/** Acessa a fila de operações estruturais preservando a chave de idempotência de origem. */
public interface StorageOperationRepository extends JpaRepository<StorageOperationEntity, Long> {

  Optional<StorageOperationEntity> findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(
      Long tenantStorageRegistryId, StorageOperationType operationType, UUID idempotencyReference);
}
