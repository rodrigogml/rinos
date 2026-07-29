package br.com.rinos.app.backend.module.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("Barreira de execução da manutenção")
class MaintenanceExecutionServiceTest {

  private static final String INSTANCE_ID = "instance-one";

  @Mock
  private MaintenanceLeaseService leaseService;

  @Mock
  private PlatformTransactionManager transactionManager;

  @Mock
  private TransactionStatus transactionStatus;

  private MaintenanceExecutionService service;
  private MaintenanceLeaseVO lease;

  /**
   * Prepara um lease estável e um gerenciador transacional controlado.
   */
  @BeforeEach
  void setUp() {
    service = new MaintenanceExecutionService(
        leaseService,
        transactionManager,
        properties());
    lease = lease();
  }

  /**
   * Comprova que cada avaliação de job produz uma nova prova persistida.
   */
  @Test
  void canStartJob_shouldRecheckLease_whenCalledMoreThanOnce() {
    when(leaseService.provesStableOwnership(lease)).thenReturn(true);

    boolean firstResult = service.canStartJob(lease);
    boolean secondResult = service.canStartJob(lease);

    assertThat(firstResult).isTrue();
    assertThat(secondResult).isTrue();
    verify(leaseService, times(2)).provesStableOwnership(lease);
  }

  /**
   * Comprova que o callback não é chamado quando a prova do lote falha.
   */
  @Test
  void executeBatch_shouldNotRunBatch_whenLeaseProofFails() {
    Runnable batch = mock(Runnable.class);
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    when(leaseService.provesStableOwnership(lease)).thenReturn(false);

    boolean result = service.executeBatch(lease, batch);

    assertThat(result).isFalse();
    verify(batch, never()).run();
    verify(transactionManager).commit(transactionStatus);
  }

  /**
   * Comprova que o lote autorizado usa transação nova com timeout de cinco minutos.
   */
  @Test
  void executeBatch_shouldRunWithConfiguredTransaction_whenLeaseProofSucceeds() {
    Runnable batch = mock(Runnable.class);
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    when(leaseService.provesStableOwnership(lease)).thenReturn(true);

    boolean result = service.executeBatch(lease, batch);

    assertThat(result).isTrue();
    verify(batch).run();
    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertThat(definition.getValue().getName()).isEqualTo("maintenance-batch");
    assertThat(definition.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(definition.getValue().getTimeout()).isEqualTo(300);
    verify(transactionManager).commit(transactionStatus);
  }

  /**
   * Comprova que uma falha do trabalho é propagada e reverte a transação do lote.
   */
  @Test
  void executeBatch_shouldRollbackAndPropagate_whenBatchFails() {
    Runnable batch = mock(Runnable.class);
    IllegalStateException failure = new IllegalStateException("batch failed");
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    when(leaseService.provesStableOwnership(lease)).thenReturn(true);
    doThrow(failure).when(batch).run();

    assertThatThrownBy(() -> service.executeBatch(lease, batch))
        .isSameAs(failure);

    verify(transactionManager).rollback(transactionStatus);
    verify(transactionManager, never()).commit(transactionStatus);
  }

  /**
   * Cria o token usado pelos cenários de execução.
   *
   * @return lease da sessão corrente
   */
  private static MaintenanceLeaseVO lease() {
    MaintenanceSessionVO owner = new MaintenanceSessionVO(
        INSTANCE_ID,
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

  /**
   * Cria as propriedades com estabilização e timeout padrão.
   *
   * @return propriedades válidas da coordenação
   */
  private static MaintenancePropertiesConfig properties() {
    return new MaintenancePropertiesConfig(
        INSTANCE_ID,
        Duration.ofMinutes(30),
        Duration.ofHours(4),
        Duration.ofMinutes(10),
        Duration.ofMinutes(5));
  }
}
