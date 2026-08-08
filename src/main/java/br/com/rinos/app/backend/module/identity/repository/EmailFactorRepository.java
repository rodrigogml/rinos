package br.com.rinos.app.backend.module.identity.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.rinos.app.backend.module.identity.entity.EmailFactorEntity;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import jakarta.persistence.LockModeType;

/** Persistência bloqueável da configuração do fator de e-mail. */
public interface EmailFactorRepository extends JpaRepository<EmailFactorEntity, Long> {
  Optional<EmailFactorEntity> findByUserId(Long userId);
  boolean existsByUserIdAndStatus(Long userId, EmailFactorStatusEnum status);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT factor FROM EmailFactorEntity factor WHERE factor.user.id = :userId")
  Optional<EmailFactorEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
