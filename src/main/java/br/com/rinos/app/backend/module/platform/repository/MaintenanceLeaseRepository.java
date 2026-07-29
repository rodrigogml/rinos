package br.com.rinos.app.backend.module.platform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.platform.entity.MaintenanceLeaseEntity;

/**
 * Acessa o lease global usado pela coordenação exclusiva de manutenção.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-28
 */
public interface MaintenanceLeaseRepository
    extends JpaRepository<MaintenanceLeaseEntity, Long> {

  /**
   * Localiza o estado vigente de uma coordenação lógica.
   *
   * @param leaseKey chave exclusiva do lease
   * @return entidade existente ou vazio quando ainda não houve aquisição
   */
  Optional<MaintenanceLeaseEntity> findByLeaseKey(String leaseKey);
}
