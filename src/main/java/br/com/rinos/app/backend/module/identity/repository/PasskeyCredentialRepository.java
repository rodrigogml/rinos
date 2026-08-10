package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.identity.entity.PasskeyCredentialEntity;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import jakarta.persistence.LockModeType;

/** Persistência bloqueável do material público das passkeys. */
public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredentialEntity, Long> {
  Optional<PasskeyCredentialEntity> findByCredentialId(byte[] credentialId);
  List<PasskeyCredentialEntity> findByPasskeyUserUserHandleAndStatusOrderById(
      byte[] userHandle, PasskeyCredentialStatusEnum status);
  List<PasskeyCredentialEntity> findByPasskeyUserUserIdOrderById(Long userId);
  long countByPasskeyUserUserIdAndStatus(Long userId, PasskeyCredentialStatusEnum status);
  long countByPasskeyUserUserIdAndStatusAndUvInitializedTrue(Long userId, PasskeyCredentialStatusEnum status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT credential FROM PasskeyCredentialEntity credential WHERE credential.passkeyUser.user.id = :userId AND credential.reference = :reference")
  Optional<PasskeyCredentialEntity> findByUserIdAndReferenceForUpdate(@Param("userId") Long userId, @Param("reference") byte[] reference);

  /** Bloqueia a credential pelo identificador WebAuthn antes de atualizar seu estado de uso. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT credential FROM PasskeyCredentialEntity credential WHERE credential.credentialId = :credentialId")
  Optional<PasskeyCredentialEntity> findByCredentialIdForUpdate(
      @Param("credentialId") byte[] credentialId);
}
