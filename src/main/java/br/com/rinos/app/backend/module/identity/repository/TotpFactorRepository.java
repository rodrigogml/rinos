package br.com.rinos.app.backend.module.identity.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.identity.entity.TotpFactorEntity;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import jakarta.persistence.LockModeType;

/** Persistência bloqueável dos fatores TOTP. */
public interface TotpFactorRepository extends JpaRepository<TotpFactorEntity, Long> {
  List<TotpFactorEntity> findByUserIdOrderById(Long userId);
  long countByUserIdAndStatus(Long userId, TotpFactorStatusEnum status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT factor FROM TotpFactorEntity factor WHERE factor.user.id = :userId AND factor.reference = :reference")
  Optional<TotpFactorEntity> findByUserIdAndReferenceForUpdate(@Param("userId") Long userId, @Param("reference") byte[] reference);
}
