package br.com.rinos.app.backend.module.storage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;

/** Consulta a execução histórica de uma versão de script dentro de um tenant. */
public interface StorageMigrationExecutionRepository
    extends JpaRepository<StorageMigrationExecutionEntity, Long> {

  Optional<StorageMigrationExecutionEntity> findByTenantStorageRegistryIdAndScriptVersion(
      Long tenantStorageRegistryId, String scriptVersion);
}
