package br.com.rinos.app.backend.module.identity.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Catálogo diário das limpezas de identidade")
class IdentityCleanupCatalogSchedulerTest {

  @Test
  void cleanup_shouldContinueIndependentTasks_afterPartialFailure() {
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
  }
}
