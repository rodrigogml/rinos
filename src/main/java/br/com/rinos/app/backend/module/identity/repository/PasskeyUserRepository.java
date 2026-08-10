package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import jakarta.persistence.LockModeType;

/** Persistência do user handle WebAuthn estável. */
public interface PasskeyUserRepository extends JpaRepository<PasskeyUserEntity, Long> {
  Optional<PasskeyUserEntity> findByUserId(Long userId);
  Optional<PasskeyUserEntity> findByUserHandle(byte[] userHandle);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT passkeyUser FROM PasskeyUserEntity passkeyUser WHERE passkeyUser.user.id = :userId")
  Optional<PasskeyUserEntity> findByUserIdForUpdate(@Param("userId") Long userId);

  /** Bloqueia o owner pelo handle estável antes de persistir uma credential do Spring. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT passkeyUser FROM PasskeyUserEntity passkeyUser WHERE passkeyUser.userHandle = :userHandle")
  Optional<PasskeyUserEntity> findByUserHandleForUpdate(@Param("userHandle") byte[] userHandle);
}
