package br.com.rinos.app.backend.module.membership.component;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.service.AccessContextCacheInvalidationService;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;
import br.com.rinos.app.backend.module.membership.service.MembershipContextInvalidationPort;

@Component
@org.springframework.context.annotation.Lazy
public class AccessMembershipContextInvalidationAdapter implements MembershipContextInvalidationPort {
  private final AccessContextRevisionService revisions;
  private final AccessContextCacheInvalidationService invalidation;

  public AccessMembershipContextInvalidationAdapter(
      AccessContextRevisionService revisions,
      AccessContextCacheInvalidationService invalidation) {
    this.revisions = revisions;
    this.invalidation = invalidation;
  }

  @Override
  public void lock(long tenantId) {
    revisions.lock(AccessScope.TENANT, tenantId);
  }

  @Override
  public long revise(long tenantId) {
    long revision = revisions.lockAndIncrement(AccessScope.TENANT, tenantId);
    invalidation.afterCommit(AccessScope.TENANT, tenantId);
    return revision;
  }
}
