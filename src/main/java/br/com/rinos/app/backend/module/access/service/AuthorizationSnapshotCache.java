package br.com.rinos.app.backend.module.access.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.config.AccessCachePropertiesConfig;

/** Cache local LRU, limitado por peso, inatividade, revisão e fronteira temporal. */
@Component
public class AuthorizationSnapshotCache {

  private final int maxWeight;
  private final Duration idleTimeout;
  private final LinkedHashMap<AccessSubjectContextKey, CacheEntry> entries =
      new LinkedHashMap<>(16, 0.75f, true);
  private int currentWeight;

  @Autowired
  public AuthorizationSnapshotCache(AccessCachePropertiesConfig properties) {
    this(properties.maxWeight(), properties.idleTimeout());
  }

  AuthorizationSnapshotCache(int maxWeight, Duration idleTimeout) {
    if (maxWeight <= 0 || idleTimeout == null || idleTimeout.isNegative()
        || idleTimeout.isZero()) {
      throw new IllegalArgumentException("authorization cache limits are invalid");
    }
    this.maxWeight = maxWeight;
    this.idleTimeout = idleTimeout;
  }

  public synchronized Optional<SubjectAccessSnapshot> get(
      AccessSubjectContextKey key, long expectedRevision, Instant at) {
    CacheEntry entry = entries.get(key);
    if (entry == null) {
      return Optional.empty();
    }
    SubjectAccessSnapshot snapshot = entry.snapshot();
    boolean stale = snapshot.contextRevision() != expectedRevision
        || !at.isBefore(entry.lastAccessAt().plus(idleTimeout))
        || snapshot.nextTemporalBoundary() != null
            && !at.isBefore(snapshot.nextTemporalBoundary());
    if (stale) {
      remove(key);
      return Optional.empty();
    }
    entries.put(key, new CacheEntry(snapshot, at));
    return Optional.of(snapshot);
  }

  public synchronized void put(SubjectAccessSnapshot snapshot, Instant at) {
    remove(snapshot.key());
    if (snapshot.weight() > maxWeight
        || snapshot.nextTemporalBoundary() != null
            && !at.isBefore(snapshot.nextTemporalBoundary())) {
      return;
    }
    entries.put(snapshot.key(), new CacheEntry(snapshot, at));
    currentWeight += snapshot.weight();
    evictOverweight();
  }

  public synchronized void invalidateContext(AccessScope scope, Long tenantId) {
    Iterator<Map.Entry<AccessSubjectContextKey, CacheEntry>> iterator =
        entries.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<AccessSubjectContextKey, CacheEntry> entry = iterator.next();
      if (entry.getKey().scope() == scope
          && java.util.Objects.equals(entry.getKey().tenantId(), tenantId)) {
        currentWeight -= entry.getValue().snapshot().weight();
        iterator.remove();
      }
    }
  }

  synchronized int size() {
    return entries.size();
  }

  synchronized int currentWeight() {
    return currentWeight;
  }

  private void evictOverweight() {
    Iterator<Map.Entry<AccessSubjectContextKey, CacheEntry>> iterator =
        entries.entrySet().iterator();
    while (currentWeight > maxWeight && iterator.hasNext()) {
      currentWeight -= iterator.next().getValue().snapshot().weight();
      iterator.remove();
    }
  }

  private void remove(AccessSubjectContextKey key) {
    CacheEntry removed = entries.remove(key);
    if (removed != null) {
      currentWeight -= removed.snapshot().weight();
    }
  }

  private record CacheEntry(SubjectAccessSnapshot snapshot, Instant lastAccessAt) {
  }
}
