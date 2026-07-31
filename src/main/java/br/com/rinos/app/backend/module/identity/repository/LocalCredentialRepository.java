package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import jakarta.persistence.LockModeType;

/**
 * Acessa credenciais locais sem expô-las fora do backend.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface LocalCredentialRepository
    extends JpaRepository<LocalCredentialEntity, Long> {

  /**
   * Localiza a credencial de uma identidade no estado exigido.
   *
   * @param userId identificador interno do usuário
   * @param status estado esperado
   * @return credencial correspondente ou vazio
   */
  Optional<LocalCredentialEntity> findByUserIdAndStatus(
      Long userId,
      LocalCredentialStatusEnum status);

  /**
   * Bloqueia a credencial de uma identidade para substituição ou remoção.
   *
   * @param userId identificador interno do usuário
   * @return credencial vigente ou vazio
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT credential
      FROM LocalCredentialEntity credential
      WHERE credential.user.id = :userId
      """)
  Optional<LocalCredentialEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
