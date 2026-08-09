package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.ReauthenticationContextEntity;
import jakarta.persistence.LockModeType;

/**
 * Persiste o vínculo imutável entre desafio, sessão e operação sensível.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface ReauthenticationContextRepository
    extends JpaRepository<ReauthenticationContextEntity, Long> {

  /** Bloqueia o contexto depois que o respectivo fluxo já foi bloqueado. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT context
      FROM ReauthenticationContextEntity context
      WHERE context.authenticationFlow.id = :flowId
      """)
  Optional<ReauthenticationContextEntity> findByAuthenticationFlowIdForUpdate(
      @Param("flowId") Long flowId);
}
