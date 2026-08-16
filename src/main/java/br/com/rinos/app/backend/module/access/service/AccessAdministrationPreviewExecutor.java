package br.com.rinos.app.backend.module.access.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.module.access.exception.AccessAdministrationConflictException;
import br.com.rinos.app.api.module.access.vo.AccessAdministrationPreview;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Executa a mesma mutacao real em transacao nova que obrigatoriamente e descartada. */
@Service
@org.springframework.context.annotation.Lazy
public class AccessAdministrationPreviewExecutor {

  private final AdministrativeContinuityEvaluator continuity;
  private final Clock clock;

  public AccessAdministrationPreviewExecutor(AdministrativeContinuityEvaluator continuity) {
    this(continuity, Clock.systemUTC());
  }

  AccessAdministrationPreviewExecutor(
      AdministrativeContinuityEvaluator continuity, Clock clock) {
    this.continuity = continuity;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void execute(
      AuthorizationContext context,
      long revision,
      String proposedChangeCode,
      boolean protectedBaselineAffected,
      Runnable mutation) {
    Instant now = clock.instant();
    AdministrativeContinuitySnapshot before = continuity.inspectContext(
        context.scope(), context.tenantId(), now);
    if (!before.sourceAvailable()) {
      rollback(context, revision, proposedChangeCode, protectedBaselineAffected,
          before, before, false, "ACL_CONTINUITY_UNAVAILABLE", now);
    }
    RuntimeException rejection = null;
    try {
      mutation.run();
    } catch (RuntimeException exception) {
      rejection = exception;
    }
    AdministrativeContinuitySnapshot after = continuity.inspectContext(
        context.scope(), context.tenantId(), now);
    boolean allowed = rejection == null && after.sourceAvailable() && after.allowed();
    String reason = allowed ? null : safeReason(rejection, after);
    rollback(context, revision, proposedChangeCode, protectedBaselineAffected,
        before, after, allowed, reason, now);
  }

  private static void rollback(
      AuthorizationContext context, long revision, String code, boolean baselineAffected,
      AdministrativeContinuitySnapshot before, AdministrativeContinuitySnapshot after,
      boolean allowed, String reason, Instant now) {
    throw new AccessAdministrationPreviewRollback(new AccessAdministrationPreview(
        context.withRevision(revision), revision, code,
        before.minimumEligibleAdministrators(), after.minimumEligibleAdministrators(),
        baselineAffected, allowed, reason, now));
  }

  private static String safeReason(
      RuntimeException rejection, AdministrativeContinuitySnapshot after) {
    if (rejection instanceof AccessAdministrationConflictException) return "ACL_CONTEXT_CHANGED";
    if (!after.sourceAvailable()) return "ACL_CONTINUITY_UNAVAILABLE";
    if (!after.allowed()) return "ACL_CONTINUITY_WOULD_BE_LOST";
    return "ACL_CHANGE_REJECTED";
  }
}
