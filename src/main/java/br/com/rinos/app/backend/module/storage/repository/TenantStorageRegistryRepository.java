package br.com.rinos.app.backend.module.storage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

/** Consulta a localização global exclusiva e interna de cada tenant. */
public interface TenantStorageRegistryRepository
    extends JpaRepository<TenantStorageRegistryEntity, Long> {

  Optional<TenantStorageRegistryEntity> findByTenantId(Long tenantId);

  Optional<TenantStorageRegistryEntity> findByPhysicalIdentifier(
      TenantPhysicalIdentifier physicalIdentifier);
}
