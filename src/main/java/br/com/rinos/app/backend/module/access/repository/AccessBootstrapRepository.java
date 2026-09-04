package br.com.rinos.app.backend.module.access.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.com.rinos.app.backend.module.access.entity.AccessBootstrapEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;

/** Persistência do singleton permanente de bootstrap. */
public interface AccessBootstrapRepository extends JpaRepository<AccessBootstrapEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select bootstrap from AccessBootstrapEntity bootstrap where bootstrap.id = 1")
  Optional<AccessBootstrapEntity> findSingletonForUpdate();
}
