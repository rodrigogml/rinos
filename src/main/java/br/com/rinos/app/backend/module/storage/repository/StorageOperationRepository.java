package br.com.rinos.app.backend.module.storage.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.storage.entity.StorageOperationEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;

/** Acessa a fila de operações estruturais preservando a chave de idempotência de origem. */
public interface StorageOperationRepository extends JpaRepository<StorageOperationEntity, Long> {

  /**
   * Reabre a operação sob lock do global antes de confirmar qualquer etapa de efeito físico.
   *
   * @param publicId protocolo público da operação
   * @return operação bloqueada até o fim da transação chamadora, quando existente
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT operation FROM StorageOperationEntity operation WHERE operation.publicId = :publicId")
  Optional<StorageOperationEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

  Optional<StorageOperationEntity> findByTenantStorageRegistryIdAndOperationTypeAndIdempotencyReference(
      Long tenantStorageRegistryId, StorageOperationType operationType, UUID idempotencyReference);

  /**
   * Busca uma operação elegível sem esperar nem abortar quando outro worker já tiver reservado a primeira posição.
   *
   * <p>O {@code SKIP LOCKED} do MySQL impede que dois despachantes concorrentes formem um deadlock sobre o mesmo
   * item. A consulta é usada exclusivamente dentro da transação que grava o lease.</p>
   *
   * @param now instante UTC usado para avaliar fila e leases vencidos
   * @return no máximo uma operação que permanece bloqueada até o fim da transação chamadora
   */
  @Query(value = """
      SELECT *
      FROM storage_operation
      WHERE (operationState IN ('QUEUED', 'RETRY_WAIT') AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now))
         OR (operationState IN ('CLAIMED', 'RUNNING') AND leaseUntil <= :now)
      ORDER BY CASE WHEN operationType = 'MIGRATE' THEN 0 ELSE 1 END, idStorageOperation
      LIMIT 1
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
  List<StorageOperationEntity> findNextEligibleForUpdate(@Param("now") Instant now);
}
