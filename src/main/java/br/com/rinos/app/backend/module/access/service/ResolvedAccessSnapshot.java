package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;
import java.util.List;

import br.com.rinos.app.api.module.access.vo.AuthorizationKeyResult;

/** Fotografia imutável das fontes ACL resolvidas para um sujeito e contexto. */
public record ResolvedAccessSnapshot(
    long contextRevision,
    List<AuthorizationKeyResult> keyResults,
    Instant nextTemporalBoundary) {

  public ResolvedAccessSnapshot {
    keyResults = List.copyOf(keyResults);
  }
}
