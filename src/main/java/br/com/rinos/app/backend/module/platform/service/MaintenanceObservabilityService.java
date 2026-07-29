package br.com.rinos.app.backend.module.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publica métricas e registros operacionais das transições do lease de manutenção.
 *
 * <p>As métricas usam somente tags de cardinalidade fixa. Identidades da instância e da sessão
 * aparecem apenas nos logs, sem credenciais, parâmetros de conexão ou conteúdo de tenant.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class MaintenanceObservabilityService {

  static final String EVENT_METRIC_NAME = "rinos.maintenance.lease.events";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MaintenanceObservabilityService.class);

  private final MaintenanceSessionService sessionService;
  private final Counter acquisitionCounter;
  private final Counter takeoverCounter;
  private final Counter renewalCounter;
  private final Counter lossCounter;
  private final Counter rejectionCounter;

  /**
   * Registra os contadores de transições conhecidas.
   *
   * @param sessionService identidade segura da execução local
   * @param meterRegistry registro de métricas da instalação
   */
  public MaintenanceObservabilityService(
      MaintenanceSessionService sessionService,
      MeterRegistry meterRegistry) {
    this.sessionService = sessionService;
    acquisitionCounter = counter(meterRegistry, "acquisition");
    takeoverCounter = counter(meterRegistry, "takeover");
    renewalCounter = counter(meterRegistry, "renewal");
    lossCounter = counter(meterRegistry, "loss");
    rejectionCounter = counter(meterRegistry, "rejection");
  }

  /**
   * Registra uma aquisição inicial comprovada.
   *
   * @param lease token persistido da aquisição
   */
  public void acquired(MaintenanceLeaseVO lease) {
    acquisitionCounter.increment();
    logLease("Lease de manutenção adquirido", lease, null);
  }

  /**
   * Registra a tomada comprovada de uma sessão anterior.
   *
   * @param lease token persistido com novo fencing
   */
  public void takenOver(MaintenanceLeaseVO lease) {
    takeoverCounter.increment();
    logLease("Lease de manutenção assumido", lease, null);
  }

  /**
   * Registra um heartbeat confirmado.
   *
   * @param lease token renovado
   */
  public void renewed(MaintenanceLeaseVO lease) {
    renewalCounter.increment();
    logLease("Lease de manutenção renovado", lease, null);
  }

  /**
   * Registra a suspensão provocada pela perda de uma prova vigente.
   *
   * @param lease último token conhecido
   * @param reason causa operacional estável e sem dados funcionais
   * @param failure falha de infraestrutura opcional
   */
  public void lost(MaintenanceLeaseVO lease, String reason, RuntimeException failure) {
    lossCounter.increment();
    if (failure == null) {
      logLease("Lease de manutenção perdido", lease, reason);
    } else {
      LOGGER.warn(
          "Lease de manutenção perdido: leaseKey={}, instanceId={}, sessionId={}, epoch={}, reason={}",
          lease.leaseKey(),
          lease.owner().instanceId(),
          lease.owner().sessionId(),
          lease.epoch(),
          reason,
          failure);
    }
  }

  /**
   * Registra uma tentativa que não conquistou ou não pôde comprovar a liderança.
   *
   * @param leaseKey chave lógica tentada
   * @param reason causa operacional estável
   * @param failure falha de infraestrutura opcional
   */
  public void rejected(String leaseKey, String reason, RuntimeException failure) {
    rejectionCounter.increment();
    MaintenanceSessionVO session = sessionService.getCurrentSession();
    if (failure == null) {
      LOGGER.info(
          "Tentativa de lease de manutenção rejeitada: leaseKey={}, instanceId={}, sessionId={}, reason={}",
          leaseKey,
          session.instanceId(),
          session.sessionId(),
          reason);
    } else {
      LOGGER.warn(
          "Tentativa de lease de manutenção rejeitada: leaseKey={}, instanceId={}, sessionId={}, reason={}",
          leaseKey,
          session.instanceId(),
          session.sessionId(),
          reason,
          failure);
    }
  }

  /**
   * Cria um contador com nome e cardinalidade estáveis.
   *
   * @param meterRegistry registro de destino
   * @param event transição catalogada
   * @return contador registrado
   */
  private static Counter counter(MeterRegistry meterRegistry, String event) {
    return Counter.builder(EVENT_METRIC_NAME)
        .description("Transições operacionais do lease de manutenção")
        .tag("event", event)
        .register(meterRegistry);
  }

  /**
   * Registra os identificadores operacionais seguros do token.
   *
   * @param message descrição estável da transição
   * @param lease token relacionado
   * @param reason causa opcional
   */
  private static void logLease(String message, MaintenanceLeaseVO lease, String reason) {
    LOGGER.info(
        "{}: leaseKey={}, instanceId={}, sessionId={}, epoch={}, reason={}",
        message,
        lease.leaseKey(),
        lease.owner().instanceId(),
        lease.owner().sessionId(),
        lease.epoch(),
        reason);
  }
}
