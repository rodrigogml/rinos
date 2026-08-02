package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("Catálogo diário das limpezas de identidade")
@ExtendWith(OutputCaptureExtension.class)
class IdentityCleanupCatalogSchedulerTest {

  @Test
  void cleanup_shouldContinueIndependentTasks_afterPartialFailure(CapturedOutput output) {
    RegistrationExpiryCleanupService registrationCleanup =
        mock(RegistrationExpiryCleanupService.class);
    OriginWindowCleanupService originCleanup = mock(OriginWindowCleanupService.class);
    IdentityTombstoneCleanupService tombstoneCleanup =
        mock(IdentityTombstoneCleanupService.class);
    Instant now = Instant.parse("2026-07-29T12:00:00Z");
    doThrow(new IllegalStateException("first task failed"))
        .when(registrationCleanup)
        .cleanup(now);
    IdentityCleanupCatalogScheduler scheduler = new IdentityCleanupCatalogScheduler(
        registrationCleanup,
        originCleanup,
        tombstoneCleanup,
        Clock.fixed(now, ZoneOffset.UTC));

    scheduler.cleanup();

    verify(registrationCleanup).cleanup(now);
    verify(originCleanup).cleanup(now);
    verify(tombstoneCleanup).cleanup(now);
    assertThat(output.getOut())
        .contains(
            "Tarefa do catálogo de limpeza falhou",
            "task=registration-expiry",
            "failureType=IllegalStateException")
        .doesNotContain("first task failed");
  }
}
