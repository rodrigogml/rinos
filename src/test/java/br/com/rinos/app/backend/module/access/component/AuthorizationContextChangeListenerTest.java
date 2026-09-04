package br.com.rinos.app.backend.module.access.component;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.service.AuthorizationContextChanged;
import br.com.rinos.app.backend.module.access.service.AuthorizationSnapshotCache;

class AuthorizationContextChangeListenerTest {

  @Test
  void onContextChanged_shouldIgnoreDuplicateAndOutOfOrderNotifications() {
    AuthorizationSnapshotCache cache = mock(AuthorizationSnapshotCache.class);
    AuthorizationContextChangeListener listener = new AuthorizationContextChangeListener(cache);

    listener.onContextChanged(new AuthorizationContextChanged(AccessScope.TENANT, 42L, 8L));
    listener.onContextChanged(new AuthorizationContextChanged(AccessScope.TENANT, 42L, 8L));
    listener.onContextChanged(new AuthorizationContextChanged(AccessScope.TENANT, 42L, 7L));
    listener.onContextChanged(new AuthorizationContextChanged(AccessScope.TENANT, 42L, 9L));

    verify(cache, times(2)).invalidateContext(AccessScope.TENANT, 42L);
  }
}
