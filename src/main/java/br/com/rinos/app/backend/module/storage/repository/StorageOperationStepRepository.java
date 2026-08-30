package br.com.rinos.app.backend.module.storage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.StorageOperationStepEntity;
import br.com.rinos.app.backend.module.storage.enums.StorageOperationStepType;

/** Localiza a única evidência de cada etapa de uma operação estrutural. */
public interface StorageOperationStepRepository
    extends JpaRepository<StorageOperationStepEntity, Long> {

  Optional<StorageOperationStepEntity> findByStorageOperationIdAndStepType(
      Long storageOperationId, StorageOperationStepType stepType);
}
