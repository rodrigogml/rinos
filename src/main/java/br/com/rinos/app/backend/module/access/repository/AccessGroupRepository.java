package br.com.rinos.app.backend.module.access.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessGroupEntity;
import br.com.rinos.app.backend.module.access.service.AccessMutationContext;
import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;

/** Persistência dos grupos isolados por contexto. */
public interface AccessGroupRepository extends JpaRepository<AccessGroupEntity, Long> {
  Optional<AccessGroupEntity> findByScopeAndTenantIdAndNormalizedName(
      AccessScope scope, Long tenantId, String normalizedName);
  List<AccessGroupEntity> findByScopeAndTenantIdOrderByNormalizedName(
      AccessScope scope, Long tenantId);
  Optional<AccessGroupEntity> findByScopeAndTenantIdAndProtectedGroupTrueAndBaselineVersion(
      AccessScope scope, Long tenantId, Integer baselineVersion);
  @Query("select new br.com.rinos.app.backend.module.access.service.AccessMutationContext(group.scope,group.tenantId) from AccessGroupEntity group where group.id=:id")
  Optional<AccessMutationContext> findMutationContext(@Param("id") Long id);
}
