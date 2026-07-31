package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.VerificationEntity;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa comprovações por hash e bloqueia provas abertas antes de transições.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface VerificationRepository extends JpaRepository<VerificationEntity, Long> {

  /**
   * Localiza somente o cadastro da prova antes de adquirir locks na ordem cadastro → prova.
   *
   * <p>A projeção escalar evita manter uma comprovação sem lock no contexto de persistência enquanto
   * a transação aguarda o bloqueio do cadastro.
   *
   * @param tokenHash SHA-256 do token apresentado
   * @return identificador do cadastro correspondente ou vazio
   */
  @Query("""
      SELECT verification.registration.id
      FROM VerificationEntity verification
      WHERE verification.tokenHash = :tokenHash
      """)
  Optional<Long> findRegistrationIdByTokenHash(
      @Param("tokenHash") byte[] tokenHash);

  /**
   * Bloqueia uma comprovação pela chave não recuperável do token.
   *
   * @param tokenHash SHA-256 do token apresentado
   * @return comprovação correspondente ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT verification
      FROM VerificationEntity verification
      WHERE verification.tokenHash = :tokenHash
      """)
  Optional<VerificationEntity> findByTokenHashForUpdate(
      @Param("tokenHash") byte[] tokenHash);

  /**
   * Bloqueia todas as provas abertas de um propósito antes de emitir uma substituta.
   *
   * @param registrationId identificador do cadastro
   * @param purpose finalidade exclusiva
   * @param status estado aberto exigido
   * @return comprovações abertas, possivelmente vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT verification
      FROM VerificationEntity verification
      WHERE verification.registration.id = :registrationId
        AND verification.purpose = :purpose
        AND verification.status = :status
      ORDER BY verification.issuedAt
      """)
  List<VerificationEntity> findByRegistrationIdAndPurposeAndStatusForUpdate(
      @Param("registrationId") Long registrationId,
      @Param("purpose") VerificationPurposeEnum purpose,
      @Param("status") VerificationStatusEnum status);

  /**
   * Bloqueia todas as provas abertas de um cadastro para encerramento integral.
   *
   * @param registrationId identificador do cadastro
   * @param status estado aberto exigido
   * @return comprovações abertas, possivelmente vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT verification
      FROM VerificationEntity verification
      WHERE verification.registration.id = :registrationId
        AND verification.status = :status
      ORDER BY verification.issuedAt
      """)
  List<VerificationEntity> findByRegistrationIdAndStatusForUpdate(
      @Param("registrationId") Long registrationId,
      @Param("status") VerificationStatusEnum status);
}
