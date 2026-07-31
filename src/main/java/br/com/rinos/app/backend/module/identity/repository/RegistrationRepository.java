package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa exclusivamente processos globais de cadastro no backend.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface RegistrationRepository extends JpaRepository<RegistrationEntity, Long> {

  /**
   * Localiza o processo 1:1 de uma identidade.
   *
   * @param userId identificador interno do usuário
   * @return processo existente ou vazio
   */
  Optional<RegistrationEntity> findByUserId(Long userId);

  /**
   * Localiza o processo de uma identidade quando estiver no estado exigido.
   *
   * @param userId identificador interno do usuário
   * @param status estado esperado do cadastro
   * @return processo correspondente ou vazio
   */
  Optional<RegistrationEntity> findByUserIdAndStatus(
      Long userId,
      RegistrationStatusEnum status);

  /**
   * Bloqueia o cadastro para serializar emissão e encerramento de comprovações.
   *
   * @param registrationId identificador interno do cadastro
   * @return cadastro persistido ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT registration
      FROM RegistrationEntity registration
      WHERE registration.id = :registrationId
      """)
  Optional<RegistrationEntity> findByIdForUpdate(
      @Param("registrationId") Long registrationId);

  /**
   * Bloqueia um lote ordenado de pendências cujo prazo absoluto já terminou.
   *
   * <p>A consulta e a exclusão permanecem na mesma transação de manutenção. O vínculo com usuário
   * é carregado para permitir a releitura do seu estado antes de qualquer deleção.
   *
   * @param status estado pendente exigido
   * @param userStatus estado pendente exigido para a identidade proprietária
   * @param maximumExpiry expiração máxima elegível
   * @param pageable limite do lote
   * @return pendências bloqueadas em ordem determinística
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT registration
      FROM RegistrationEntity registration
      JOIN FETCH registration.user
      WHERE registration.status = :status
        AND registration.user.status = :userStatus
        AND registration.expiresAt <= :maximumExpiry
      ORDER BY registration.expiresAt, registration.id
      """)
  List<RegistrationEntity> findExpiredPendingBatchForUpdate(
      @Param("status") RegistrationStatusEnum status,
      @Param("userStatus") UserStatusEnum userStatus,
      @Param("maximumExpiry") Instant maximumExpiry,
      Pageable pageable);
}
