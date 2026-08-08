package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeEntity;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import jakarta.persistence.LockModeType;

/** Persistência dos hashes individuais de recuperação. */
public interface RecoveryCodeRepository extends JpaRepository<RecoveryCodeEntity, Long> {
  long countByCodeSetIdAndStatus(Long codeSetId, RecoveryCodeStatusEnum status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT code FROM RecoveryCodeEntity code WHERE code.codeSet.id = :setId ORDER BY code.ordinal")
  List<RecoveryCodeEntity> findByCodeSetIdForUpdate(@Param("setId") Long setId);
}
