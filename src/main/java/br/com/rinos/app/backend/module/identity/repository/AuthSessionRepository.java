package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa sessões globais e serializa validação, atividade e revogação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, Long> {

  /** Bloqueia uma sessão pelo digest do seletor apresentado no cookie. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT session FROM AuthSessionEntity session WHERE session.selectorHash = :selectorHash")
  Optional<AuthSessionEntity> findBySelectorHashForUpdate(
      @Param("selectorHash") byte[] selectorHash);

  /** Bloqueia uma sessão do usuário pela referência exclusiva de gestão. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT session
      FROM AuthSessionEntity session
      WHERE session.user.id = :userId
        AND session.publicReference = :publicReference
      """)
  Optional<AuthSessionEntity> findByUserIdAndPublicReferenceForUpdate(
      @Param("userId") Long userId,
      @Param("publicReference") byte[] publicReference);

  /** Bloqueia todas as sessões do usuário em ordem determinística. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT session
      FROM AuthSessionEntity session
      WHERE session.user.id = :userId
        AND session.status = :status
      ORDER BY session.id
      """)
  List<AuthSessionEntity> findByUserIdAndStatusForUpdate(
      @Param("userId") Long userId,
      @Param("status") AuthSessionStatusEnum status);

  /** Lista sessões para projeção segura sem expor seus verificadores. */
  @Query("""
      SELECT session
      FROM AuthSessionEntity session
      WHERE session.user.id = :userId
      ORDER BY session.lastActivityAt DESC, session.id DESC
      """)
  List<AuthSessionEntity> findByUserIdForManagement(@Param("userId") Long userId);

  /** Bloqueia sessões ativas vencidas por qualquer limite temporal. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT session
      FROM AuthSessionEntity session
      WHERE session.status = :status
        AND (session.absoluteExpiresAt <= :occurredAt OR session.idleExpiresAt <= :occurredAt)
      ORDER BY session.id
      """)
  List<AuthSessionEntity> findExpiredByStatusForUpdate(
      @Param("status") AuthSessionStatusEnum status,
      @Param("occurredAt") Instant occurredAt);

  /** Remove apenas sessões terminais depois da retenção operacional. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      DELETE FROM AuthSessionEntity session
      WHERE session.status <> :activeStatus
        AND session.updatedAt < :retentionCutoff
      """)
  int deleteTerminalBefore(
      @Param("activeStatus") AuthSessionStatusEnum activeStatus,
      @Param("retentionCutoff") Instant retentionCutoff);
}
