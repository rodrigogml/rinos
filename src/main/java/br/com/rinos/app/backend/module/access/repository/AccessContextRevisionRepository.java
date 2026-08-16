package br.com.rinos.app.backend.module.access.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessContextRevisionEntity;
import jakarta.persistence.LockModeType;

/** Resolve e bloqueia a revisão autoritativa de cada contexto. */
public interface AccessContextRevisionRepository
    extends JpaRepository<AccessContextRevisionEntity, Long> {

  @Modifying
  @Query(value = """
      INSERT INTO access_contextRevision (scopeType, idTenant, revision)
      VALUES (:scopeType, :tenantId, 0)
      ON DUPLICATE KEY UPDATE revision = revision
      """, nativeQuery = true)
  void ensureContext(@Param("scopeType") String scopeType, @Param("tenantId") Long tenantId);

  @Query("SELECT revision FROM AccessContextRevisionEntity revision "
      + "WHERE revision.scope = :scope AND revision.tenantId IS NULL")
  Optional<AccessContextRevisionEntity> findGlobal(@Param("scope") AccessScope scope);

  Optional<AccessContextRevisionEntity> findByScopeAndTenantId(AccessScope scope, Long tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT revision FROM AccessContextRevisionEntity revision "
      + "WHERE revision.scope = :scope AND revision.tenantId IS NULL")
  Optional<AccessContextRevisionEntity> findGlobalForUpdate(@Param("scope") AccessScope scope);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT revision FROM AccessContextRevisionEntity revision "
      + "WHERE revision.scope = :scope AND revision.tenantId = :tenantId")
  Optional<AccessContextRevisionEntity> findForUpdate(
      @Param("scope") AccessScope scope, @Param("tenantId") Long tenantId);
}
