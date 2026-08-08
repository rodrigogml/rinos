package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa fluxos opacos e serializa todas as suas transições persistentes.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthenticationFlowRepository
    extends JpaRepository<AuthenticationFlowEntity, Long> {

  /** Bloqueia o fluxo identificado pelo SHA-256 da referência apresentada. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT flow
      FROM AuthenticationFlowEntity flow
      WHERE flow.referenceHash = :referenceHash
      """)
  Optional<AuthenticationFlowEntity> findByReferenceHashForUpdate(
      @Param("referenceHash") byte[] referenceHash);

  /** Bloqueia um fluxo pelo identificador interno durante trabalhos de manutenção. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT flow
      FROM AuthenticationFlowEntity flow
      WHERE flow.id = :flowId
      """)
  Optional<AuthenticationFlowEntity> findByIdForUpdate(@Param("flowId") Long flowId);

  /** Bloqueia fluxos abertos já vencidos para expiração lógica em lote. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT flow
      FROM AuthenticationFlowEntity flow
      WHERE flow.status = :status
        AND flow.expiresAt <= :occurredAt
      ORDER BY flow.id
      """)
  List<AuthenticationFlowEntity> findExpiredByStatusForUpdate(
      @Param("status") AuthenticationFlowStatusEnum status,
      @Param("occurredAt") Instant occurredAt);

  /**
   * Remove somente fluxos terminais cuja retenção já encerrou; filhos são eliminados por cascade.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      DELETE FROM AuthenticationFlowEntity flow
      WHERE flow.status <> :openStatus
        AND flow.updatedAt < :retentionCutoff
      """)
  int deleteTerminalBefore(
      @Param("openStatus") AuthenticationFlowStatusEnum openStatus,
      @Param("retentionCutoff") Instant retentionCutoff);
}
