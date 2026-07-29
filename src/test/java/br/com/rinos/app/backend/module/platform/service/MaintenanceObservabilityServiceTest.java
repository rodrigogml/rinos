package br.com.rinos.app.backend.module.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@DisplayName("Observabilidade do lease de manutenção")
class MaintenanceObservabilityServiceTest {

  @Mock
  private MaintenanceSessionService sessionService;

  private SimpleMeterRegistry meterRegistry;
  private MaintenanceObservabilityService service;
  private ListAppender<ILoggingEvent> logAppender;

  /**
   * Prepara métricas e captura isolada dos registros operacionais.
   */
  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new MaintenanceObservabilityService(sessionService, meterRegistry);
    Logger logger = (Logger) LoggerFactory.getLogger(MaintenanceObservabilityService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  /**
   * Remove o capturador e fecha o registro descartável.
   */
  @AfterEach
  void tearDown() {
    Logger logger = (Logger) LoggerFactory.getLogger(MaintenanceObservabilityService.class);
    logger.detachAppender(logAppender);
    logAppender.stop();
    meterRegistry.close();
  }

  /**
   * Comprova que todas as transições obrigatórias possuem contadores independentes.
   */
  @Test
  void events_shouldIncrementDedicatedCounters_whenTransitionsAreRecorded() {
    MaintenanceLeaseVO lease = lease();
    when(sessionService.getCurrentSession()).thenReturn(lease.owner());

    service.acquired(lease);
    service.takenOver(lease);
    service.renewed(lease);
    service.lost(lease, "heartbeat-rejected", null);
    service.rejected(lease.leaseKey(), "owned-by-another-session", null);

    assertThat(counter("acquisition")).isEqualTo(1);
    assertThat(counter("takeover")).isEqualTo(1);
    assertThat(counter("renewal")).isEqualTo(1);
    assertThat(counter("loss")).isEqualTo(1);
    assertThat(counter("rejection")).isEqualTo(1);
  }

  /**
   * Comprova que o registro de rejeição contém somente o contexto operacional necessário.
   */
  @Test
  void rejected_shouldLogSafeOperationalContext_whenAttemptLoses() {
    MaintenanceLeaseVO lease = lease();
    when(sessionService.getCurrentSession()).thenReturn(lease.owner());

    service.rejected(lease.leaseKey(), "owned-by-another-session", null);

    List<String> messages = logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
    assertThat(messages).singleElement().asString()
        .contains("leaseKey=global-maintenance")
        .contains("instanceId=instance-one")
        .contains("sessionId=65cb579b-1e02-4a89-83c6-f9a8af8f83ea")
        .contains("reason=owned-by-another-session")
        .doesNotContain("password", "jdbc:");
  }

  private double counter(String event) {
    return meterRegistry.get(MaintenanceObservabilityService.EVENT_METRIC_NAME)
        .tag("event", event)
        .counter()
        .count();
  }

  /**
   * Cria um token seguro usado pelos eventos.
   *
   * @return lease da sessão local
   */
  private static MaintenanceLeaseVO lease() {
    MaintenanceSessionVO owner = new MaintenanceSessionVO(
        "instance-one",
        UUID.fromString("65cb579b-1e02-4a89-83c6-f9a8af8f83ea"));
    return new MaintenanceLeaseVO(
        "global-maintenance",
        owner,
        3,
        Instant.parse("2026-07-29T10:00:00Z"),
        Instant.parse("2026-07-29T10:30:00Z"),
        Instant.parse("2026-07-29T14:30:00Z"),
        5);
  }
}
