package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationWindowEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationWindowOperationEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa janelas antifraude por identificador protegido sem recuperar a identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthenticationWindowRepository
    extends JpaRepository<AuthenticationWindowEntity, Long> {

  /**
   * Garante atomicamente a existência da janela ativa, inclusive entre instâncias.
   *
   * <p>O conflito na UK preserva integralmente a janela já existente; a mutação ocorre depois,
   * sob lock pessimista.
   */
  @Modifying(flushAutomatically = true)
  @Query(value = """
      INSERT INTO security_authenticationWindow (
        identifierDigest, keyVersion, operation, windowStartedAt, windowEndsAt,
        failureCount, activeMarker
      ) VALUES (
        :identifierDigest, :keyVersion, :operation, :windowStartedAt, :windowEndsAt,
        0, TRUE
      )
      ON DUPLICATE KEY UPDATE id = id
      """, nativeQuery = true)
  int ensureActive(
      @Param("identifierDigest") byte[] identifierDigest,
      @Param("keyVersion") String keyVersion,
      @Param("operation") String operation,
      @Param("windowStartedAt") Instant windowStartedAt,
      @Param("windowEndsAt") Instant windowEndsAt);

  /** Bloqueia a janela ativa correspondente ao digest, versão e operação. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT window
      FROM AuthenticationWindowEntity window
      WHERE window.identifierDigest = :identifierDigest
        AND window.keyVersion = :keyVersion
        AND window.operation = :operation
        AND window.activeMarker = TRUE
      """)
  Optional<AuthenticationWindowEntity> findActiveForUpdate(
      @Param("identifierDigest") byte[] identifierDigest,
      @Param("keyVersion") String keyVersion,
      @Param("operation") AuthenticationWindowOperationEnum operation);

  /** Bloqueia janelas ativas vencidas para encerramento lógico. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT window
      FROM AuthenticationWindowEntity window
      WHERE window.activeMarker = TRUE
        AND window.windowEndsAt <= :occurredAt
      ORDER BY window.id
      """)
  List<AuthenticationWindowEntity> findExpiredActiveForUpdate(
      @Param("occurredAt") Instant occurredAt);

  /** Remove janelas encerradas após o período de retenção. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      DELETE FROM AuthenticationWindowEntity window
      WHERE window.activeMarker IS NULL
        AND window.updatedAt < :retentionCutoff
      """)
  int deleteClosedBefore(@Param("retentionCutoff") Instant retentionCutoff);
}
