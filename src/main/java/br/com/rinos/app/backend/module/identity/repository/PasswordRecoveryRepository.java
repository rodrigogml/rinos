package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.PasswordRecoveryEntity;
import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa provas de recuperação por usuário ou hash não recuperável.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public interface PasswordRecoveryRepository extends JpaRepository<PasswordRecoveryEntity, Long> {

  /**
   * Conta emissões recentes do usuário para limitação neutra.
   *
   * @param userId usuário proprietário
   * @param issuedAfter início inclusivo da janela
   * @return quantidade emitida
   */
  long countByUserIdAndIssuedAtGreaterThanEqual(Long userId, Instant issuedAfter);

  /**
   * Bloqueia as provas do usuário no estado informado.
   *
   * @param userId usuário proprietário
   * @param status estado exigido
   * @return provas ordenadas por emissão
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT recovery
      FROM PasswordRecoveryEntity recovery
      WHERE recovery.user.id = :userId
        AND recovery.status = :status
      ORDER BY recovery.issuedAt
      """)
  List<PasswordRecoveryEntity> findByUserIdAndStatusForUpdate(
      @Param("userId") Long userId,
      @Param("status") PasswordRecoveryStatusEnum status);

  /**
   * Bloqueia uma prova pelo hash não recuperável.
   *
   * @param tokenHash SHA-256 da prova apresentada
   * @return prova encontrada ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT recovery
      FROM PasswordRecoveryEntity recovery
      WHERE recovery.tokenHash = :tokenHash
      """)
  Optional<PasswordRecoveryEntity> findByTokenHashForUpdate(
      @Param("tokenHash") byte[] tokenHash);

  /**
   * Exclui um lote que excedeu a retenção.
   *
   * @param cutoff última atualização máxima elegível
   * @param batchSize tamanho máximo do lote
   * @return linhas removidas
   */
  @Modifying
  @Query(value = """
      DELETE FROM identity_passwordRecovery
      WHERE updatedAt <= :cutoff
      ORDER BY updatedAt, id
      LIMIT :batchSize
      """, nativeQuery = true)
  int deleteRetentionBatch(
      @Param("cutoff") Instant cutoff,
      @Param("batchSize") int batchSize);
}
