package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.List;

/** Snapshot local de fontes de um único sujeito e contexto. */
public record SubjectAccessSnapshot(
    AccessSubjectContextKey key,
    long contextRevision,
    List<AccessSourceSnapshot> sources,
    Instant loadedAt,
    Instant nextTemporalBoundary) {

  public SubjectAccessSnapshot {
    sources = List.copyOf(sources);
    if (contextRevision < 0 || key == null || loadedAt == null) {
      throw new IllegalArgumentException("subject access snapshot is inconsistent");
    }
  }

  public int weight() {
    return 1 + sources.size();
  }
}
