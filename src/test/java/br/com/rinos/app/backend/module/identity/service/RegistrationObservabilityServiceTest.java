package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.enums.RegistrationLifecycleEventEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Observabilidade sanitizada do cadastro")
class RegistrationObservabilityServiceTest {

  @Test
  void recordOperation_shouldUseOnlyClosedLowCardinalityTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RegistrationObservabilityService service =
        new RegistrationObservabilityService(registry);

    service.recordOperation(
        RegistrationOperationEnum.ACTIVATE,
        "ACTIVATED",
        UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab"),
        Instant.parse("2026-07-29T12:00:00Z"),
        Instant.parse("2026-07-29T12:00:01Z"));

    assertThat(registry.counter(
        RegistrationObservabilityService.OPERATION_COUNTER_NAME,
        "operation",
        "activate",
        "result",
        "activated").count()).isEqualTo(1);
    assertThat(registry.timer(
        RegistrationObservabilityService.OPERATION_DURATION_NAME,
        "operation",
        "activate",
        "result",
        "activated").totalTime(java.util.concurrent.TimeUnit.SECONDS))
        .isEqualTo(1);
    assertThat(registry.getMeters())
        .flatExtracting(meter -> meter.getId().getTags())
        .extracting(io.micrometer.core.instrument.Tag::getKey)
        .containsOnly("operation", "result", "event");
    assertThat(registry.getMeters())
        .extracting(Meter::getId)
        .noneMatch(id -> id.getTags().stream()
            .anyMatch(tag -> tag.getValue().contains("@")
                || tag.getValue().contains("95f6724a")));
    assertThat(registry.counter(
        RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME,
        "event",
        "activated").count()).isEqualTo(1);
  }

  @Test
  void recordLifecycle_shouldCountBatch_withoutPersistentIdentifiers() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RegistrationObservabilityService service =
        new RegistrationObservabilityService(registry);

    service.recordLifecycle(RegistrationLifecycleEventEnum.EXPIRED, 3);

    assertThat(registry.counter(
        RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME,
        "event",
        "expired").count()).isEqualTo(3);
  }

  @Test
  void recordOperation_shouldDeriveOnlyConfirmedLifecycleEffects() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RegistrationObservabilityService service =
        new RegistrationObservabilityService(registry);

    service.recordOperation(
        RegistrationOperationEnum.START,
        "EMAIL_DISPATCH_FAILED",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.CANCELLATION_CONFIRM,
        "CANCELLED",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.RESEND,
        "RATE_LIMITED",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.ACTIVATE,
        "ALREADY_ACTIVE",
        null,
        Instant.EPOCH,
        Instant.EPOCH);

    assertThat(registry.counter(
        RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME,
        "event",
        "pending_created").count()).isEqualTo(1);
    assertThat(registry.counter(
        RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME,
        "event",
        "cancelled").count()).isEqualTo(1);
    assertThat(registry.counter(
        RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME,
        "event",
        "blocked").count()).isEqualTo(1);
    assertThat(registry.find(RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME)
        .tag("event", "activated")
        .counter()).isNull();
  }

  @Test
  void metrics_shouldExposeResendBlockActivationCancellationAndCleanup() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RegistrationObservabilityService service =
        new RegistrationObservabilityService(registry);

    service.recordOperation(
        RegistrationOperationEnum.RESEND,
        "EMAIL_SENT",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.START,
        "RATE_LIMITED",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.ACTIVATE,
        "ACTIVATED",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.CANCELLATION_CONFIRM,
        "CANCELLED",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordOperation(
        RegistrationOperationEnum.START,
        "UNAVAILABLE",
        null,
        Instant.EPOCH,
        Instant.EPOCH);
    service.recordLifecycle(RegistrationLifecycleEventEnum.EXPIRED, 2);

    assertOperation(registry, "resend", "email_sent");
    assertOperation(registry, "start", "rate_limited");
    assertOperation(registry, "activate", "activated");
    assertOperation(registry, "cancellation_confirm", "cancelled");
    assertOperation(registry, "start", "unavailable");
    assertLifecycle(registry, "blocked", 1);
    assertLifecycle(registry, "activated", 1);
    assertLifecycle(registry, "cancelled", 1);
    assertLifecycle(registry, "expired", 2);
  }

  @Test
  void recordOperation_shouldRejectFreeTextResult() {
    RegistrationObservabilityService service =
        new RegistrationObservabilityService(new SimpleMeterRegistry());

    assertThatThrownBy(() -> service.recordOperation(
        RegistrationOperationEnum.START,
        "person@example.com",
        null,
        Instant.EPOCH,
        Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void assertOperation(
      SimpleMeterRegistry registry,
      String operation,
      String result) {
    assertThat(registry.counter(
        RegistrationObservabilityService.OPERATION_COUNTER_NAME,
        "operation",
        operation,
        "result",
        result).count()).isEqualTo(1);
    assertThat(registry.timer(
        RegistrationObservabilityService.OPERATION_DURATION_NAME,
        "operation",
        operation,
        "result",
        result).count()).isEqualTo(1);
  }

  private static void assertLifecycle(
      SimpleMeterRegistry registry,
      String event,
      double count) {
    assertThat(registry.counter(
        RegistrationObservabilityService.LIFECYCLE_COUNTER_NAME,
        "event",
        event).count()).isEqualTo(count);
  }
}
