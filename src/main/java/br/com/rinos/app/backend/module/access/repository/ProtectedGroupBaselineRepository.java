package br.com.rinos.app.backend.module.access.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.ProtectedGroupBaselineEntity;
import br.com.rinos.app.backend.module.access.enums.ProtectedBaselineStatus;

/** Persistência das versões explícitas de continuidade administrativa. */
public interface ProtectedGroupBaselineRepository
    extends JpaRepository<ProtectedGroupBaselineEntity, Long> {
  Optional<ProtectedGroupBaselineEntity> findByScopeAndBaselineVersion(
      AccessScope scope, int baselineVersion);
  Optional<ProtectedGroupBaselineEntity> findFirstByScopeAndStatusOrderByBaselineVersionDesc(
      AccessScope scope, ProtectedBaselineStatus status);
}
