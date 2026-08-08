package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import jakarta.persistence.LockModeType;

/** Persistência bloqueável dos conjuntos de recuperação. */
public interface RecoveryCodeSetRepository extends JpaRepository<RecoveryCodeSetEntity, Long> {
  Optional<RecoveryCodeSetEntity> findByUserIdAndStatus(Long userId, RecoveryCodeSetStatusEnum status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT codeSet FROM RecoveryCodeSetEntity codeSet WHERE codeSet.user.id = :userId AND codeSet.status = :status")
  Optional<RecoveryCodeSetEntity> findByUserIdAndStatusForUpdate(@Param("userId") Long userId, @Param("status") RecoveryCodeSetStatusEnum status);
}
