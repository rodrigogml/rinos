package br.com.rinos.app.backend.module.membership.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.service.AccessContextCacheInvalidationService;
import br.com.rinos.app.backend.module.access.service.AccessContextRevisionService;

class AccessMembershipContextInvalidationAdapterTest {

  @Test
  void shouldIncrementTenantRevisionAndScheduleLocalInvalidation() {
    var revisions = mock(AccessContextRevisionService.class);
    var invalidation = mock(AccessContextCacheInvalidationService.class);
    when(revisions.lockAndIncrement(AccessScope.TENANT, 42L)).thenReturn(9L);

    var adapter = new AccessMembershipContextInvalidationAdapter(revisions, invalidation);

    adapter.lock(42L);
    assertThat(adapter.revise(42L)).isEqualTo(9L);
    verify(revisions).lock(AccessScope.TENANT, 42L);
    verify(revisions).lockAndIncrement(AccessScope.TENANT, 42L);
    verify(invalidation).afterCommit(AccessScope.TENANT, 42L);
  }
}
