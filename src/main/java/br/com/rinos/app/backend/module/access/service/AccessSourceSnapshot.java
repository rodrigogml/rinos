package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceStatus;
import br.com.rinos.app.api.module.access.enums.AuthorizationSourceType;
import br.com.rinos.app.api.module.access.vo.AuthorizationRuleSource;

/** Fonte ACL imutável; sua vigência é recalculada a cada decisão. */
public record AccessSourceSnapshot(
    long accessKeyId,
    AuthorizationSourceType type,
    String sourceReference,
    AccessRuleEffect effect,
    boolean registrallyActive,
    Instant ruleValidFrom,
    Instant ruleValidUntil,
    Instant subjectValidFrom,
    Instant subjectValidUntil) {

  public AuthorizationRuleSource evaluate(Instant at) {
    AuthorizationSourceStatus status = !registrallyActive
        ? AuthorizationSourceStatus.INACTIVE
        : isFuture(ruleValidFrom, at) || isFuture(subjectValidFrom, at)
            ? AuthorizationSourceStatus.FUTURE
        : isExpired(ruleValidUntil, at) || isExpired(subjectValidUntil, at)
            ? AuthorizationSourceStatus.EXPIRED
        : AuthorizationSourceStatus.CURRENT;
    return new AuthorizationRuleSource(
        type, sourceReference, effect, status, ruleValidFrom, ruleValidUntil);
  }

  public java.util.stream.Stream<Instant> temporalBoundaries() {
    return java.util.stream.Stream.of(
        ruleValidFrom, ruleValidUntil, subjectValidFrom, subjectValidUntil)
        .filter(java.util.Objects::nonNull);
  }

  private static boolean isFuture(Instant boundary, Instant at) {
    return boundary != null && at.isBefore(boundary);
  }

  private static boolean isExpired(Instant boundary, Instant at) {
    return boundary != null && !at.isBefore(boundary);
  }
}
