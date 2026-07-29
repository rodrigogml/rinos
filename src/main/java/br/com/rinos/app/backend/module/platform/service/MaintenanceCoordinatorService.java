package br.com.rinos.app.backend.module.platform.service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;

/**
 * Mantém a candidatura local e suspende trabalho quando a liderança deixa de ser comprovável.
 *
 * <p>O estado em memória nunca declara liderança por si só: ele apenas conserva o último token
 * persistido. Jobs e lotes continuam sujeitos a uma nova prova no banco global. Qualquer resultado
 * negativo ou falha de infraestrutura invalida o token antes de propagar ou retornar o resultado.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class MaintenanceCoordinatorService {

  private final MaintenanceLeaseService leaseService;
  private final MaintenanceExecutionService executionService;
  private final MaintenanceObservabilityService observabilityService;
  private final AtomicReference<MaintenanceLeaseVO> activeLease = new AtomicReference<>();

  /**
   * Cria a coordenação sobre os serviços persistente e transacional.
   *
   * @param leaseService aquisição, renovação e prova persistidas
   * @param executionService barreira transacional de jobs e lotes
   * @param observabilityService métricas e logs das transições
   */
  public MaintenanceCoordinatorService(
      MaintenanceLeaseService leaseService,
      MaintenanceExecutionService executionService,
      MaintenanceObservabilityService observabilityService) {
    this.leaseService = leaseService;
    this.executionService = executionService;
    this.observabilityService = observabilityService;
  }

  /**
   * Tenta adquirir o lease e substitui qualquer token local pela resposta persistida.
   *
   * @param leaseKey chave lógica do lease global
   * @return {@code true} quando a sessão atual é a proprietária persistida
   * @throws RuntimeException quando o banco global não permite concluir a tentativa
   */
  public boolean tryAcquire(String leaseKey) {
    Objects.requireNonNull(leaseKey, "leaseKey must not be null");
    try {
      Optional<MaintenanceLeaseVO> acquiredLease = leaseService.tryAcquire(leaseKey);
      activeLease.set(acquiredLease.orElse(null));
      acquiredLease.ifPresent(this::observeAcquisition);
      if (acquiredLease.isEmpty()) {
        observabilityService.rejected(leaseKey, "owned-by-another-session", null);
      }
      return acquiredLease.isPresent();
    } catch (RuntimeException exception) {
      activeLease.set(null);
      observabilityService.rejected(leaseKey, "database-unavailable", exception);
      throw exception;
    }
  }

  /**
   * Executa o heartbeat e suspende a sessão quando a renovação não for comprovada.
   *
   * @return {@code true} quando o token foi renovado; {@code false} quando não há liderança vigente
   * @throws RuntimeException quando o banco global não permite concluir a renovação
   */
  public boolean renewLease() {
    MaintenanceLeaseVO expectedLease = activeLease.get();
    if (expectedLease == null) {
      return false;
    }
    try {
      Optional<MaintenanceLeaseVO> renewedLease = leaseService.renew(expectedLease);
      if (renewedLease.isEmpty()) {
        if (activeLease.compareAndSet(expectedLease, null)) {
          observabilityService.lost(expectedLease, "heartbeat-rejected", null);
        }
        return false;
      }
      MaintenanceLeaseVO currentLease = renewedLease.orElseThrow();
      boolean stored = activeLease.compareAndSet(expectedLease, currentLease);
      if (stored) {
        observabilityService.renewed(currentLease);
      } else {
        observabilityService.rejected(
            expectedLease.leaseKey(), "local-token-changed", null);
      }
      return stored;
    } catch (RuntimeException exception) {
      if (activeLease.compareAndSet(expectedLease, null)) {
        observabilityService.lost(expectedLease, "heartbeat-failed", exception);
      }
      throw exception;
    }
  }

  /**
   * Revalida a liderança antes do job e suspende a sessão diante de prova negativa.
   *
   * @return {@code true} somente quando o job pode iniciar
   * @throws RuntimeException quando o banco global não permite realizar a prova
   */
  public boolean canStartJob() {
    MaintenanceLeaseVO expectedLease = activeLease.get();
    if (expectedLease == null) {
      return false;
    }
    try {
      boolean allowed = executionService.canStartJob(expectedLease);
      if (!allowed) {
        if (activeLease.compareAndSet(expectedLease, null)) {
          observabilityService.lost(expectedLease, "job-proof-rejected", null);
        }
      }
      return allowed;
    } catch (RuntimeException exception) {
      if (activeLease.compareAndSet(expectedLease, null)) {
        observabilityService.lost(expectedLease, "job-proof-failed", exception);
      }
      throw exception;
    }
  }

  /**
   * Executa um lote no próximo ponto transacional seguro ou o mantém suspenso.
   *
   * @param batch trabalho atômico e idempotente
   * @return {@code true} quando o lote foi confirmado; {@code false} quando permaneceu suspenso
   * @throws NullPointerException quando o lote é nulo
   * @throws RuntimeException quando o lote ou sua infraestrutura transacional falha
   */
  public boolean executeBatch(Runnable batch) {
    Objects.requireNonNull(batch, "batch must not be null");
    MaintenanceLeaseVO expectedLease = activeLease.get();
    if (expectedLease == null) {
      return false;
    }
    try {
      boolean executed = executionService.executeBatch(expectedLease, batch);
      if (!executed) {
        if (activeLease.compareAndSet(expectedLease, null)) {
          observabilityService.lost(expectedLease, "batch-proof-rejected", null);
        }
      }
      return executed;
    } catch (RuntimeException exception) {
      if (activeLease.compareAndSet(expectedLease, null)) {
        observabilityService.lost(expectedLease, "batch-failed", exception);
      }
      throw exception;
    }
  }

  /**
   * Distingue a primeira aquisição de uma tomada por meio do fencing persistido.
   *
   * @param lease token adquirido
   */
  private void observeAcquisition(MaintenanceLeaseVO lease) {
    if (lease.epoch() == 1) {
      observabilityService.acquired(lease);
    } else {
      observabilityService.takenOver(lease);
    }
  }
}
