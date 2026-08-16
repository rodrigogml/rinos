package br.com.rinos.app.backend.module.access.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Agenda invalidação local somente depois de uma mutação ACL confirmada. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessContextCacheInvalidationService {

  private final AuthorizationSnapshotCache cache;

  public AccessContextCacheInvalidationService(AuthorizationSnapshotCache cache) {
    this.cache = cache;
  }

  public void afterCommit(AccessScope scope, Long tenantId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      cache.invalidateContext(scope, tenantId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        cache.invalidateContext(scope, tenantId);
      }
    });
  }
}
