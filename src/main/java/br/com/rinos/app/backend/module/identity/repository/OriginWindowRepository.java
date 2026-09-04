package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.OriginWindowEntity;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginPolicyEnum;

/**
 * Acessa janelas temporárias de prevenção de abuso.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface OriginWindowRepository extends JpaRepository<OriginWindowEntity, Long> {

  /**
   * Localiza uma janela pela chave composta persistente.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy política contabilizada
   * @param windowStartedAt início determinístico
   * @return janela correspondente ou vazio
   */
  Optional<OriginWindowEntity>
      findByOriginAddressAndOperationAndPolicyAndWindowStartedAt(
          byte[] originAddress,
          OriginOperationEnum operation,
          OriginPolicyEnum policy,
          Instant windowStartedAt);

  /**
   * Lista janelas cujo fim antecede o limite de retenção calculado.
   *
   * @param maximumWindowEnd fim máximo elegível
   * @return janelas expiradas
   */
  List<OriginWindowEntity> findByWindowEndsAtBefore(Instant maximumWindowEnd);

  /**
   * Relê a janela corrente usando o relógio do banco.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy contador canônico
   * @return janela vigente ou vazio
   */
  @Query("""
      SELECT window
      FROM OriginWindowEntity window
      WHERE window.originAddress = :originAddress
        AND window.operation = :operation
        AND window.policy = :policy
        AND window.activeMarker = TRUE
        AND window.windowEndsAt > CURRENT_TIMESTAMP
      """)
  Optional<OriginWindowEntity> findCurrent(
      @Param("originAddress") byte[] originAddress,
      @Param("operation") OriginOperationEnum operation,
      @Param("policy") OriginPolicyEnum policy);

  /**
   * Localiza somente uma janela cujo bloqueio ainda esteja vigente no relógio do banco.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy contador canônico
   * @return janela bloqueada ou vazio quando a origem pode tentar novamente
   */
  @Query("""
      SELECT window
      FROM OriginWindowEntity window
      WHERE window.originAddress = :originAddress
        AND window.operation = :operation
        AND window.policy = :policy
        AND window.activeMarker = TRUE
        AND window.blockedUntil > CURRENT_TIMESTAMP
      """)
  Optional<OriginWindowEntity> findCurrentBlocked(
      @Param("originAddress") byte[] originAddress,
      @Param("operation") OriginOperationEnum operation,
      @Param("policy") OriginPolicyEnum policy);

  /**
   * Converte janela vencida em histórico antes de disputar uma nova chave ativa.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy contador canônico
   * @return linhas encerradas
   */
  @Modifying
  @Query(value = """
      UPDATE security_originWindow
      SET activeMarker = NULL,
          version = version + 1
      WHERE originAddress = :originAddress
        AND operation = :operation
        AND policy = :policy
        AND activeMarker = TRUE
        AND windowEndsAt <= CURRENT_TIMESTAMP(6)
        AND (blockedUntil IS NULL OR blockedUntil <= CURRENT_TIMESTAMP(6))
      """, nativeQuery = true)
  int closeExpired(
      @Param("originAddress") byte[] originAddress,
      @Param("operation") String operation,
      @Param("policy") String policy);

  /**
   * Cria a janela de primeira utilização; a UK ativa escolhe uma vencedora concorrente.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy contador canônico
   * @param windowMicroseconds duração em microssegundos
   * @return uma linha criada ou zero quando outra transação venceu
   */
  @Modifying
  @Query(value = """
      INSERT INTO security_originWindow (
        originAddress,
        operation,
        policy,
        activeMarker,
        windowStartedAt,
        windowEndsAt,
        eventCount,
        version
      ) VALUES (
        :originAddress,
        :operation,
        :policy,
        TRUE,
        CURRENT_TIMESTAMP(6),
        TIMESTAMPADD(MICROSECOND, :windowMicroseconds, CURRENT_TIMESTAMP(6)),
        0,
        0
      )
      ON DUPLICATE KEY UPDATE id = id
      """, nativeQuery = true)
  int createActiveIfAbsent(
      @Param("originAddress") byte[] originAddress,
      @Param("operation") String operation,
      @Param("policy") String policy,
      @Param("windowMicroseconds") long windowMicroseconds);

  /**
   * Reserva atomicamente uma criação sem ultrapassar o limite.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy contador canônico
   * @param absoluteLimit limite exclusivo
   * @return uma linha incrementada ou zero quando bloqueada
   */
  @Modifying
  @Query(value = """
      UPDATE security_originWindow
      SET eventCount = eventCount + 1,
          version = version + 1
      WHERE originAddress = :originAddress
        AND operation = :operation
        AND policy = :policy
        AND activeMarker = TRUE
        AND windowEndsAt > CURRENT_TIMESTAMP(6)
        AND eventCount < :absoluteLimit
      """, nativeQuery = true)
  int incrementBelowLimit(
      @Param("originAddress") byte[] originAddress,
      @Param("operation") String operation,
      @Param("policy") String policy,
      @Param("absoluteLimit") int absoluteLimit);

  /**
   * Registra bloqueio temporário sem estender um bloqueio concorrente já vigente.
   *
   * @param originAddress endereço binário
   * @param operation operação protegida
   * @param policy contador canônico
   * @param blockMicroseconds duração positiva do bloqueio
   * @return linhas atualizadas
   */
  @Modifying
  @Query(value = """
      UPDATE security_originWindow
      SET blockedUntil = CASE
            WHEN blockedUntil IS NULL OR blockedUntil <= CURRENT_TIMESTAMP(6)
              THEN TIMESTAMPADD(MICROSECOND, :blockMicroseconds, CURRENT_TIMESTAMP(6))
            ELSE blockedUntil
          END,
          version = version + 1
      WHERE originAddress = :originAddress
        AND operation = :operation
        AND policy = :policy
        AND activeMarker = TRUE
        AND windowEndsAt > CURRENT_TIMESTAMP(6)
      """, nativeQuery = true)
  int blockCurrent(
      @Param("originAddress") byte[] originAddress,
      @Param("operation") String operation,
      @Param("policy") String policy,
      @Param("blockMicroseconds") long blockMicroseconds);

  /**
   * Exclui um lote histórico que excedeu a retenção.
   *
   * @param cutoff fim máximo permitido
   * @param batchSize limite da transação
   * @return linhas removidas
   */
  @Modifying
  @Query(value = """
      DELETE FROM security_originWindow
      WHERE GREATEST(windowEndsAt, COALESCE(blockedUntil, windowEndsAt)) <= :cutoff
      ORDER BY windowEndsAt, id
      LIMIT :batchSize
      """, nativeQuery = true)
  int deleteRetentionBatch(
      @Param("cutoff") Instant cutoff,
      @Param("batchSize") int batchSize);
}
