package br.com.rinos.app.backend.module.access.component;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.service.AuthorizationContextChanged;
import br.com.rinos.app.backend.module.access.service.AuthorizationSnapshotCache;

/** Consome notificações opcionais sem substituir a revisão persistida. */
@Component
@org.springframework.context.annotation.Lazy
public class AuthorizationContextChangeListener {

  private final AuthorizationSnapshotCache cache;
  private final Map<ContextKey, Long> lastRevisionByContext = new HashMap<>();

  public AuthorizationContextChangeListener(AuthorizationSnapshotCache cache) {
    this.cache = cache;
  }

  public synchronized void onContextChanged(AuthorizationContextChanged event) {
    ContextKey key = new ContextKey(event.scope(), event.tenantId());
    long lastRevision = lastRevisionByContext.getOrDefault(key, -1L);
    if (event.newRevision() <= lastRevision) {
      return;
    }
    lastRevisionByContext.put(key, event.newRevision());
    cache.invalidateContext(event.scope(), event.tenantId());
  }

  private record ContextKey(AccessScope scope, Long tenantId) {
  }
}
