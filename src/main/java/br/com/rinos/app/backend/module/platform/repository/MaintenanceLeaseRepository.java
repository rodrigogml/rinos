package br.com.rinos.app.backend.module.platform.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

  /**
   * Lê a referência temporal do banco global com precisão de microssegundos.
   *
   * <p>Esta leitura atende comparações e diagnósticos. Aquisição, heartbeat e expiração devem
   * calcular seus instantes diretamente na própria instrução de mutação com
   * {@code UTC_TIMESTAMP(6)}, sem transportar o relógio da JVM para a persistência.
   *
   * @return instante UTC produzido pelo MySQL
   */
  @Query(value = "SELECT UTC_TIMESTAMP(6)", nativeQuery = true)
  Instant readDatabaseTime();
}
