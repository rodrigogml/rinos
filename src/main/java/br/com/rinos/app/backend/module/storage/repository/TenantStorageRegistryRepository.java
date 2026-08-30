package br.com.rinos.app.backend.module.storage.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.com.rinos.app.backend.module.storage.entity.TenantStorageRegistryEntity;
import br.com.rinos.app.backend.module.storage.vo.TenantPhysicalIdentifier;

/** Consulta a localização global exclusiva e interna de cada tenant. */
public interface TenantStorageRegistryRepository
    extends JpaRepository<TenantStorageRegistryEntity, Long> {

  Optional<TenantStorageRegistryEntity> findByTenantId(Long tenantId);

  Optional<TenantStorageRegistryEntity> findByPhysicalIdentifier(
      TenantPhysicalIdentifier physicalIdentifier);

  /**
   * Reserva os tenants prontos enquanto o startup decide quais precisam ser incompatibilizados para migration.
   *
   * <p>O lock evita que duas instâncias que sobem em paralelo criem operações concorrentes ou publiquem um tenant
   * como migrando em momentos diferentes. A seleção é restrita a registros prontos: tenants já migrando, em
   * quarentena ou no provisionamento não pertencem a este fluxo.</p>
   *
   * @return registros prontos bloqueados até o término da transação chamadora
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT registry FROM TenantStorageRegistryEntity registry "
      + "WHERE registry.storageState = br.com.rinos.app.backend.module.storage.enums.TenantStorageState.READY")
  java.util.List<TenantStorageRegistryEntity> findAllReadyForMigrationScheduling();
}
