package br.com.rinos.app.backend.module.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;

@ExtendWith(MockitoExtension.class)
@DisplayName("Estado seguro da coordenação de manutenção")
class MaintenanceCoordinatorServiceTest {

  @Mock
  private MaintenanceLeaseService leaseService;

  @Mock
  private MaintenanceExecutionService executionService;

  private MaintenanceCoordinatorService service;
  private MaintenanceLeaseVO lease;

  /**
   * Prepara o coordenador sem liderança inicial.
   */
  @BeforeEach
  void setUp() {
    service = new MaintenanceCoordinatorService(leaseService, executionService);
    lease = lease(3, 5);
  }

  /**
   * Comprova que a aquisição persistida habilita uma posterior prova de job.
   */
  @Test
  void tryAcquire_shouldEnableJobProof_whenCurrentSessionWins() {
    when(leaseService.tryAcquire("global-maintenance")).thenReturn(Optional.of(lease));
    when(executionService.canStartJob(lease)).thenReturn(true);

    boolean acquired = service.tryAcquire("global-maintenance");
    boolean allowed = service.canStartJob();

    assertThat(acquired).isTrue();
    assertThat(allowed).isTrue();
  }

  /**
   * Comprova que um heartbeat recusado suspende os próximos lotes.
   */
  @Test
  void renewLease_shouldSuspendBatches_whenRenewalIsRejected() {
    Runnable batch = mock(Runnable.class);
    acquireLease();
    when(leaseService.renew(lease)).thenReturn(Optional.empty());

    boolean renewed = service.renewLease();
    boolean executed = service.executeBatch(batch);

    assertThat(renewed).isFalse();
    assertThat(executed).isFalse();
    verify(executionService, never()).executeBatch(lease, batch);
  }

  /**
   * Comprova que erro do banco durante heartbeat suspende antes de propagar a falha.
   */
  @Test
  void renewLease_shouldSuspendAndPropagate_whenDatabaseIsUnavailable() {
    acquireLease();
    IllegalStateException failure = new IllegalStateException("database unavailable");
    when(leaseService.renew(lease)).thenThrow(failure);

    assertThatThrownBy(service::renewLease).isSameAs(failure);

    assertThat(service.canStartJob()).isFalse();
    verify(executionService, never()).canStartJob(lease);
  }

  /**
   * Comprova que fencing divergente observado na prova suspende novas execuções.
   */
  @Test
  void canStartJob_shouldSuspendBatches_whenFencingProofIsRejected() {
    Runnable batch = mock(Runnable.class);
    acquireLease();
    when(executionService.canStartJob(lease)).thenReturn(false);

    boolean allowed = service.canStartJob();
    boolean executed = service.executeBatch(batch);

    assertThat(allowed).isFalse();
    assertThat(executed).isFalse();
    verify(executionService, never()).executeBatch(lease, batch);
  }

  /**
   * Comprova que indisponibilidade durante a prova do lote suspende tentativas posteriores.
   */
  @Test
  void executeBatch_shouldSuspendAndPropagate_whenDatabaseProofFails() {
    Runnable firstBatch = mock(Runnable.class);
    Runnable secondBatch = mock(Runnable.class);
    acquireLease();
    IllegalStateException failure = new IllegalStateException("database unavailable");
    when(executionService.executeBatch(lease, firstBatch)).thenThrow(failure);

    assertThatThrownBy(() -> service.executeBatch(firstBatch)).isSameAs(failure);

    assertThat(service.executeBatch(secondBatch)).isFalse();
    verify(executionService, never()).executeBatch(lease, secondBatch);
  }

  /**
   * Comprova que uma nova aquisição restaura a capacidade de provar a liderança.
   */
  @Test
  void tryAcquire_shouldResumeProof_whenPreviousLeaseWasSuspended() {
    MaintenanceLeaseVO renewedLease = lease(4, 6);
    acquireLease();
    when(executionService.canStartJob(lease)).thenReturn(false);
    service.canStartJob();
    when(leaseService.tryAcquire("global-maintenance"))
        .thenReturn(Optional.of(renewedLease));
    when(executionService.canStartJob(renewedLease)).thenReturn(true);

    boolean acquired = service.tryAcquire("global-maintenance");
    boolean allowed = service.canStartJob();

    assertThat(acquired).isTrue();
    assertThat(allowed).isTrue();
  }

  /**
   * Instala o lease padrão no estado local por meio do contrato público.
   */
  private void acquireLease() {
    when(leaseService.tryAcquire("global-maintenance")).thenReturn(Optional.of(lease));
    assertThat(service.tryAcquire("global-maintenance")).isTrue();
  }

  /**
   * Cria um token com fencing controlado.
   *
   * @param epoch fencing token
   * @param version versão persistida
   * @return lease da sessão local
   */
  private static MaintenanceLeaseVO lease(long epoch, long version) {
    MaintenanceSessionVO owner = new MaintenanceSessionVO(
        "instance-one",
        UUID.fromString("65cb579b-1e02-4a89-83c6-f9a8af8f83ea"));
    return new MaintenanceLeaseVO(
        "global-maintenance",
        owner,
        epoch,
        Instant.parse("2026-07-29T10:00:00Z"),
        Instant.parse("2026-07-29T10:30:00Z"),
        Instant.parse("2026-07-29T14:30:00Z"),
        version);
  }
}
