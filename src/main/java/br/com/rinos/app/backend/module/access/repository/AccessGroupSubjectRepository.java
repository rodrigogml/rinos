package br.com.rinos.app.backend.module.access.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.backend.module.access.entity.AccessGroupSubjectEntity;
import br.com.rinos.app.backend.module.access.service.AccessMutationContext;
import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param;

/** Persistência das associações temporais entre sujeitos e grupos. */
public interface AccessGroupSubjectRepository
    extends JpaRepository<AccessGroupSubjectEntity, Long> {
  List<AccessGroupSubjectEntity> findByUserId(Long userId);
  List<AccessGroupSubjectEntity> findByAccountMembershipId(Long accountMembershipId);
  List<AccessGroupSubjectEntity> findByGroupIdIn(List<Long> groupIds);
  Optional<AccessGroupSubjectEntity> findByGroupIdAndUserId(Long groupId, Long userId);
  Optional<AccessGroupSubjectEntity> findByGroupIdAndAccountMembershipId(
      Long groupId, Long accountMembershipId);
  @Query("select new br.com.rinos.app.backend.module.access.service.AccessMutationContext(group.scope,group.tenantId) from AccessGroupSubjectEntity subject, AccessGroupEntity group where subject.id=:id and group.id=subject.groupId")
  Optional<AccessMutationContext> findMutationContext(@Param("id") Long id);
}
