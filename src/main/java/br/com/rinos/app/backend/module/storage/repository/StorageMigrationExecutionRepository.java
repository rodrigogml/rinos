package br.com.rinos.app.backend.module.storage.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.StorageMigrationExecutionEntity;

/** Consulta a execução histórica de uma versão de script dentro de um tenant. */
public interface StorageMigrationExecutionRepository
    extends JpaRepository<StorageMigrationExecutionEntity, Long> {

  Optional<StorageMigrationExecutionEntity> findByTenantStorageRegistryIdAndScriptVersion(
      Long tenantStorageRegistryId, String scriptVersion);

  /**
   * Retorna as evidências de uma operação em ordem determinística para confirmação ou quarentena posterior.
   *
   * @param storageOperationId identificador interno da operação estrutural
   * @return evidências associadas à operação, ordenadas por versão do script
   */
  List<StorageMigrationExecutionEntity> findAllByStorageOperationIdOrderByScriptVersion(Long storageOperationId);

  /**
   * Retorna todas as evidências históricas do tenant para validar ausência de lacunas após o baseline.
   *
   * @param tenantStorageRegistryId identificador interno do inventário do tenant
   * @return evidências ordenadas pela versão de script
   */
  List<StorageMigrationExecutionEntity> findAllByTenantStorageRegistryIdOrderByScriptVersion(
      Long tenantStorageRegistryId);
}
