package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;

class AuthorizationSnapshotCacheTest {

  private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

  @Test
  void get_shouldRejectRevisionMismatchAndTemporalBoundary() {
    AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(10, Duration.ofMinutes(30));
    AccessSubjectContextKey key = new AccessSubjectContextKey(AccessScope.TENANT, 42L, 7L);
    cache.put(snapshot(key, 3L, NOW.plusSeconds(60), 1), NOW);

    assertThat(cache.get(key, 4L, NOW.plusSeconds(1))).isEmpty();
    cache.put(snapshot(key, 3L, NOW.plusSeconds(60), 1), NOW);
    assertThat(cache.get(key, 3L, NOW.plusSeconds(60))).isEmpty();
  }

  @Test
  void put_shouldEvictLeastRecentlyUsedEntriesByWeight() {
    AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(4, Duration.ofMinutes(30));
    AccessSubjectContextKey first = new AccessSubjectContextKey(AccessScope.GLOBAL, null, 1L);
    AccessSubjectContextKey second = new AccessSubjectContextKey(AccessScope.GLOBAL, null, 2L);
    AccessSubjectContextKey third = new AccessSubjectContextKey(AccessScope.GLOBAL, null, 3L);
    cache.put(snapshot(first, 1L, null, 1), NOW);
    cache.put(snapshot(second, 1L, null, 1), NOW);
    cache.get(first, 1L, NOW.plusSeconds(1));
    cache.put(snapshot(third, 1L, null, 1), NOW.plusSeconds(2));

    assertThat(cache.get(second, 1L, NOW.plusSeconds(3))).isEmpty();
    assertThat(cache.get(first, 1L, NOW.plusSeconds(3))).isPresent();
    assertThat(cache.get(third, 1L, NOW.plusSeconds(3))).isPresent();
    assertThat(cache.currentWeight()).isEqualTo(4);
  }

  @Test
  void invalidateContext_shouldPreserveOtherTenants() {
    AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(20, Duration.ofMinutes(30));
    AccessSubjectContextKey tenant42 = new AccessSubjectContextKey(AccessScope.TENANT, 42L, 7L);
    AccessSubjectContextKey tenant99 = new AccessSubjectContextKey(AccessScope.TENANT, 99L, 7L);
    cache.put(snapshot(tenant42, 1L, null, 1), NOW);
    cache.put(snapshot(tenant99, 1L, null, 1), NOW);

    cache.invalidateContext(AccessScope.TENANT, 42L);

    assertThat(cache.get(tenant42, 1L, NOW.plusSeconds(1))).isEmpty();
    assertThat(cache.get(tenant99, 1L, NOW.plusSeconds(1))).isPresent();
  }

  @Test
  void revisionShouldProtectSecondInstanceWhenNotificationIsLost() {
    AuthorizationSnapshotCache firstInstance =
        new AuthorizationSnapshotCache(10, Duration.ofMinutes(30));
    AuthorizationSnapshotCache secondInstance =
        new AuthorizationSnapshotCache(10, Duration.ofMinutes(30));
    AccessSubjectContextKey key = new AccessSubjectContextKey(AccessScope.TENANT, 42L, 7L);
    firstInstance.put(snapshot(key, 5L, null, 1), NOW);
    secondInstance.put(snapshot(key, 5L, null, 1), NOW);

    firstInstance.invalidateContext(AccessScope.TENANT, 42L);

    assertThat(secondInstance.get(key, 6L, NOW.plusSeconds(1))).isEmpty();
  }

  @Test
  void get_shouldExpireEntryAfterInactivity() {
    AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(10, Duration.ofMinutes(5));
    AccessSubjectContextKey key = new AccessSubjectContextKey(AccessScope.GLOBAL, null, 7L);
    cache.put(snapshot(key, 1L, null, 1), NOW);

    assertThat(cache.get(key, 1L, NOW.plus(Duration.ofMinutes(5)))).isEmpty();
  }

  @Test
  void invalidationService_shouldInvalidateOnlyAfterCommit() {
    AuthorizationSnapshotCache cache = new AuthorizationSnapshotCache(10, Duration.ofMinutes(30));
    AccessSubjectContextKey key = new AccessSubjectContextKey(AccessScope.GLOBAL, null, 7L);
    cache.put(snapshot(key, 1L, null, 1), NOW);
    AccessContextCacheInvalidationService service =
        new AccessContextCacheInvalidationService(cache);
    TransactionSynchronizationManager.initSynchronization();
    try {
      service.afterCommit(AccessScope.GLOBAL, null);
      assertThat(cache.get(key, 1L, NOW.plusSeconds(1))).isPresent();
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(synchronization -> synchronization.afterCommit());
      assertThat(cache.get(key, 1L, NOW.plusSeconds(2))).isEmpty();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static SubjectAccessSnapshot snapshot(
      AccessSubjectContextKey key,
      long revision,
      Instant nextBoundary,
      int sourceCount) {
    List<AccessSourceSnapshot> sources = java.util.stream.IntStream.range(0, sourceCount)
        .mapToObj(index -> new AccessSourceSnapshot(
            index + 1L, AuthorizationSourceType.DIRECT, "rule:" + index,
            AccessRuleEffect.PERMITIR, true, null, null, null, null))
        .toList();
    return new SubjectAccessSnapshot(key, revision, sources, NOW, nextBoundary);
  }
}
