package br.com.rinos.app.backend.module.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

class AccessAdministrationPreviewExecutorTest {

  private static final Instant NOW = Instant.parse("2026-08-16T15:00:00Z");

  @Test
  void execute_shouldReportBeforeAndAfterAndAlwaysRequestRollback() {
    AdministrativeContinuityEvaluator continuity = mock(AdministrativeContinuityEvaluator.class);
    when(continuity.inspectContext(AccessScope.TENANT, 7L, NOW)).thenReturn(
        AdministrativeContinuitySnapshot.available(3),
        AdministrativeContinuitySnapshot.available(2));
    AccessAdministrationPreviewExecutor executor = new AccessAdministrationPreviewExecutor(
        continuity, Clock.fixed(NOW, ZoneOffset.UTC));
    AtomicBoolean mutated = new AtomicBoolean();

    Throwable failure = catchThrowable(() -> executor.execute(
        AuthorizationContext.tenant(7L), 11L, "ACCESS_RULE_SAVE", true,
        () -> mutated.set(true)));

    assertThat(mutated).isTrue();
    assertThat(failure).isInstanceOf(AccessAdministrationPreviewRollback.class);
    var preview = ((AccessAdministrationPreviewRollback) failure).preview();
    assertThat(preview.eligibleAdministratorsBefore()).isEqualTo(3);
    assertThat(preview.eligibleAdministratorsAfter()).isEqualTo(2);
    assertThat(preview.confirmationAllowed()).isTrue();
    assertThat(preview.safeReasonCode()).isNull();
  }

  @Test
  void execute_shouldDisableConfirmationWhenContinuityWouldBeLost() {
    AdministrativeContinuityEvaluator continuity = mock(AdministrativeContinuityEvaluator.class);
    when(continuity.inspectContext(AccessScope.GLOBAL, null, NOW)).thenReturn(
        AdministrativeContinuitySnapshot.available(1),
        AdministrativeContinuitySnapshot.available(0));
    AccessAdministrationPreviewExecutor executor = new AccessAdministrationPreviewExecutor(
        continuity, Clock.fixed(NOW, ZoneOffset.UTC));

    Throwable failure = catchThrowable(() -> executor.execute(
        AuthorizationContext.global(), 4L, "ACCESS_GROUP_DEACTIVATE", true,
        () -> { throw new IllegalArgumentException("administrative continuity would be lost"); }));

    var preview = ((AccessAdministrationPreviewRollback) failure).preview();
    assertThat(preview.confirmationAllowed()).isFalse();
    assertThat(preview.safeReasonCode()).isEqualTo("ACL_CONTINUITY_WOULD_BE_LOST");
    assertThat(preview.eligibleAdministratorsAfter()).isZero();
  }
}
