package br.com.rinos.app.backend.module.storage.vo;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.backend.module.storage.enums.StorageOperationType;

/**
 * Posse transitória e interna de uma operação estrutural reclamada pela manutenção.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-30
 */
public record StorageOperationClaimVO(UUID operationPublicId, Long registryId,
    StorageOperationType operationType, String leaseOwner, Instant leaseUntil) {
}
