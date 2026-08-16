package br.com.rinos.app.backend.module.access.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.entity.AccessRuleEntity;

/** Persistência das regras correntes, diretas e de grupo. */
public interface AccessRuleRepository extends JpaRepository<AccessRuleEntity, Long> {
  Optional<AccessRuleEntity> findByScopeAndTenantIdAndUserIdAndAccessKeyId(
      AccessScope scope, Long tenantId, Long userId, Long accessKeyId);
  Optional<AccessRuleEntity> findByScopeAndTenantIdAndAccountMembershipIdAndAccessKeyId(
      AccessScope scope, Long tenantId, Long membershipId, Long accessKeyId);
  Optional<AccessRuleEntity> findByScopeAndTenantIdAndAccessGroupIdAndAccessKeyId(
      AccessScope scope, Long tenantId, Long groupId, Long accessKeyId);
  List<AccessRuleEntity> findByScopeAndTenantId(AccessScope scope, Long tenantId);
  List<AccessRuleEntity> findByScopeAndTenantIdAndUserId(
      AccessScope scope, Long tenantId, Long userId);
  List<AccessRuleEntity> findByScopeAndTenantIdAndAccountMembershipId(
      AccessScope scope, Long tenantId, Long membershipId);
  List<AccessRuleEntity> findByScopeAndTenantIdAndAccessGroupIdIn(
      AccessScope scope, Long tenantId, List<Long> groupIds);
}
