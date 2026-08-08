package br.com.rinos.app.backend.module.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationProofEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa provas após o bloqueio do fluxo proprietário e protege consumo e substituição.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthenticationProofRepository
    extends JpaRepository<AuthenticationProofEntity, Long> {

  /** Bloqueia a prova de um tipo e estado no fluxo já bloqueado pela transação. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT proof
      FROM AuthenticationProofEntity proof
      WHERE proof.flow.id = :flowId
        AND proof.type = :type
        AND proof.status = :status
      """)
  Optional<AuthenticationProofEntity> findByFlowIdAndTypeAndStatusForUpdate(
      @Param("flowId") Long flowId,
      @Param("type") AuthenticationProofTypeEnum type,
      @Param("status") AuthenticationProofStatusEnum status);

  /** Bloqueia todas as provas de um estado após o lock do fluxo proprietário. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT proof
      FROM AuthenticationProofEntity proof
      WHERE proof.flow.id = :flowId
        AND proof.status = :status
      ORDER BY proof.issuedAt, proof.id
      """)
  List<AuthenticationProofEntity> findByFlowIdAndStatusForUpdate(
      @Param("flowId") Long flowId,
      @Param("status") AuthenticationProofStatusEnum status);

  /** Localiza os fluxos que possuem prova aberta vencida sem adquirir lock fora de ordem. */
  @Query("""
      SELECT DISTINCT proof.flow.id
      FROM AuthenticationProofEntity proof
      WHERE proof.status = :status
        AND proof.expiresAt <= :occurredAt
      ORDER BY proof.flow.id
      """)
  List<Long> findFlowIdsWithExpiredProofs(
      @Param("status") AuthenticationProofStatusEnum status,
      @Param("occurredAt") Instant occurredAt);

  /** Bloqueia a prova mais recente do tipo para classificar replay sem alterar seu conteúdo. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<AuthenticationProofEntity> findFirstByFlowIdAndTypeOrderByIssuedAtDesc(
      Long flowId,
      AuthenticationProofTypeEnum type);

  /** Remove provas terminais retidas em fluxos ainda existentes. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      DELETE FROM AuthenticationProofEntity proof
      WHERE proof.status <> :openStatus
        AND proof.updatedAt < :retentionCutoff
      """)
  int deleteTerminalBefore(
      @Param("openStatus") AuthenticationProofStatusEnum openStatus,
      @Param("retentionCutoff") Instant retentionCutoff);
}
