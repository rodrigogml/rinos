package br.com.rinos.app.backend.module.platform.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Cria a aquisição inicial quando ainda não existe linha para a chave.
   *
   * <p>Uma colisão na chave única preserva integralmente o proprietário existente. Os instantes
   * são calculados pelo MySQL dentro da própria instrução.
   *
   * @param leaseKey chave exclusiva do lease
   * @param instanceId identidade estável da instância
   * @param sessionId UUID textual da inicialização
   * @param leaseTimeoutMicroseconds duração do lease em microssegundos
   * @return quantidade de linhas afetadas conforme o contrato do MySQL
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      INSERT INTO platform_maintenanceLease (
        leaseKey,
        instanceId,
        sessionId,
        epoch,
        acquiredAt,
        heartbeatAt,
        leaseUntil,
        version
      )
      VALUES (
        :leaseKey,
        :instanceId,
        :sessionId,
        1,
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6),
        TIMESTAMPADD(MICROSECOND, :leaseTimeoutMicroseconds, UTC_TIMESTAMP(6)),
        0
      )
      ON DUPLICATE KEY UPDATE leaseKey = leaseKey
      """, nativeQuery = true)
  int createIfAbsent(
      @Param("leaseKey") String leaseKey,
      @Param("instanceId") String instanceId,
      @Param("sessionId") String sessionId,
      @Param("leaseTimeoutMicroseconds") long leaseTimeoutMicroseconds);

  /**
   * Toma atomicamente um lease expirado e incrementa seu fencing token.
   *
   * @param leaseKey chave exclusiva do lease
   * @param instanceId identidade estável da instância candidata
   * @param sessionId UUID textual da sessão candidata
   * @param leaseTimeoutMicroseconds duração do novo lease em microssegundos
   * @return {@code 1} somente para a sessão vencedora; {@code 0} quando o lease ainda está vigente
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE platform_maintenanceLease
      SET instanceId = :instanceId,
          sessionId = :sessionId,
          epoch = epoch + 1,
          acquiredAt = UTC_TIMESTAMP(6),
          heartbeatAt = UTC_TIMESTAMP(6),
          leaseUntil = TIMESTAMPADD(
              MICROSECOND,
              :leaseTimeoutMicroseconds,
              UTC_TIMESTAMP(6)),
          version = version + 1
      WHERE leaseKey = :leaseKey
        AND leaseUntil <= UTC_TIMESTAMP(6)
      """, nativeQuery = true)
  int takeOverIfExpired(
      @Param("leaseKey") String leaseKey,
      @Param("instanceId") String instanceId,
      @Param("sessionId") String sessionId,
      @Param("leaseTimeoutMicroseconds") long leaseTimeoutMicroseconds);

  /**
   * Renova o lease somente quando todos os tokens do proprietário continuam vigentes.
   *
   * @param leaseKey chave exclusiva do lease
   * @param instanceId identidade estável da instância proprietária
   * @param sessionId UUID textual da sessão proprietária
   * @param epoch fencing token esperado
   * @param version versão otimista esperada
   * @param leaseTimeoutMicroseconds duração renovada em microssegundos
   * @return {@code 1} quando renovado; {@code 0} diante de perda, expiração ou token divergente
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      UPDATE platform_maintenanceLease
      SET heartbeatAt = UTC_TIMESTAMP(6),
          leaseUntil = TIMESTAMPADD(
              MICROSECOND,
              :leaseTimeoutMicroseconds,
              UTC_TIMESTAMP(6)),
          version = version + 1
      WHERE leaseKey = :leaseKey
        AND instanceId = :instanceId
        AND sessionId = :sessionId
        AND epoch = :epoch
        AND version = :version
        AND leaseUntil > UTC_TIMESTAMP(6)
      """, nativeQuery = true)
  int renewIfOwned(
      @Param("leaseKey") String leaseKey,
      @Param("instanceId") String instanceId,
      @Param("sessionId") String sessionId,
      @Param("epoch") long epoch,
      @Param("version") long version,
      @Param("leaseTimeoutMicroseconds") long leaseTimeoutMicroseconds);

  /**
   * Comprova propriedade, fencing, vigência e estabilização usando somente o relógio do MySQL.
   *
   * @param leaseKey chave exclusiva do lease
   * @param instanceId identidade estável da instância proprietária
   * @param sessionId UUID textual da sessão proprietária
   * @param epoch fencing token esperado
   * @param stabilizationMicroseconds espera mínima depois da aquisição
   * @return {@code 1} quando a sessão está apta a iniciar trabalho; {@code 0} nos demais estados
   */
  @Query(value = """
      SELECT COUNT(*)
      FROM platform_maintenanceLease
      WHERE leaseKey = :leaseKey
        AND instanceId = :instanceId
        AND sessionId = :sessionId
        AND epoch = :epoch
        AND leaseUntil > UTC_TIMESTAMP(6)
        AND TIMESTAMPADD(
            MICROSECOND,
            :stabilizationMicroseconds,
            acquiredAt) <= UTC_TIMESTAMP(6)
      """, nativeQuery = true)
  long countStableOwnership(
      @Param("leaseKey") String leaseKey,
      @Param("instanceId") String instanceId,
      @Param("sessionId") String sessionId,
      @Param("epoch") long epoch,
      @Param("stabilizationMicroseconds") long stabilizationMicroseconds);
}
