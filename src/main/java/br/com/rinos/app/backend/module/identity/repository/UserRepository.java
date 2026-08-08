package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa exclusivamente identidades globais persistidas no backend.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface UserRepository extends JpaRepository<UserEntity, Long> {

  /**
   * Localiza uma identidade pela chave normalizada do e-mail.
   *
   * @param normalizedEmail e-mail previamente normalizado
   * @return identidade existente ou vazio
   */
  Optional<UserEntity> findByNormalizedEmail(String normalizedEmail);

  /**
   * Localiza uma identidade pela chave de e-mail e estado.
   *
   * @param normalizedEmail e-mail previamente normalizado
   * @param status estado exigido
   * @return identidade correspondente ou vazio
   */
  Optional<UserEntity> findByNormalizedEmailAndStatus(
      String normalizedEmail,
      UserStatusEnum status);

  /**
   * Bloqueia uma identidade por e-mail antes de anexar um vínculo externo.
   *
   * @param normalizedEmail chave canônica do e-mail
   * @return identidade bloqueada ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT user
      FROM UserEntity user
      WHERE user.normalizedEmail = :normalizedEmail
      """)
  Optional<UserEntity> findByNormalizedEmailForUpdate(
      @Param("normalizedEmail") String normalizedEmail);

  /** Bloqueia a identidade antes de alterar sua coleção de métodos e fatores. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT user FROM UserEntity user WHERE user.id = :userId")
  Optional<UserEntity> findByIdForUpdate(@Param("userId") Long userId);
}
