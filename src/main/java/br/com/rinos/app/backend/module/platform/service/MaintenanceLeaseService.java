package br.com.rinos.app.backend.module.platform.service;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.platform.entity.MaintenanceLeaseEntity;
import br.com.rinos.app.backend.module.platform.repository.MaintenanceLeaseRepository;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

/**
 * Coordena aquisição, tomada e renovação do lease global de manutenção.
 *
 * <p>As decisões concorrentes são executadas por mutações condicionais no MySQL. O serviço nunca
 * usa o relógio local para criar ou validar a vigência persistida.
 *
 * <p>A criação é tardia para que diagnósticos de configuração e migração possam ser emitidos antes
 * de qualquer consumidor da persistência de manutenção.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class MaintenanceLeaseService {

  private static final int MAXIMUM_LEASE_KEY_LENGTH = 64;

  private final MaintenanceLeaseRepository repository;
  private final MaintenanceSessionService sessionService;
  private final long leaseTimeoutMicroseconds;
  private final long stabilizationMicroseconds;

  /**
   * Cria o coordenador usando a sessão da inicialização e o timeout validado.
   *
   * @param repository acesso persistente ao lease global
   * @param sessionService identidade efêmera da execução atual
   * @param properties propriedades tipadas da coordenação
   */
  public MaintenanceLeaseService(
      MaintenanceLeaseRepository repository,
      MaintenanceSessionService sessionService,
      MaintenancePropertiesConfig properties) {
    this.repository = repository;
    this.sessionService = sessionService;
    leaseTimeoutMicroseconds = toMicroseconds(properties.leaseTimeout());
    stabilizationMicroseconds = toMicroseconds(properties.stabilizationPeriod());
  }

  /**
   * Tenta adquirir a chave ausente ou tomar sua linha quando já expirou.
   *
   * <p>A repetição pela sessão proprietária é idempotente e retorna o token vigente sem estender o
   * prazo. Outra sessão recebe vazio enquanto o lease estiver válido.
   *
   * @param leaseKey chave lógica obrigatória, com até 64 caracteres
   * @return token da sessão vencedora ou vazio quando outra sessão mantém a propriedade
   * @throws IllegalArgumentException quando a chave está vazia ou excede o limite persistente
   * @throws NullPointerException quando a chave é nula
   */
  @Transactional
  public Optional<MaintenanceLeaseVO> tryAcquire(String leaseKey) {
    validateLeaseKey(leaseKey);
    MaintenanceSessionVO currentSession = sessionService.getCurrentSession();
    String sessionId = currentSession.sessionId().toString();

    repository.createIfAbsent(
        leaseKey,
        currentSession.instanceId(),
        sessionId,
        leaseTimeoutMicroseconds);
    repository.takeOverIfExpired(
        leaseKey,
        currentSession.instanceId(),
        sessionId,
        leaseTimeoutMicroseconds);

    return repository.findByLeaseKey(leaseKey)
        .map(this::toValue)
        .filter(lease -> lease.owner().equals(currentSession));
  }

  /**
   * Renova um token somente se ele ainda representa o proprietário persistido e não expirou.
   *
   * @param expectedLease token completo obtido pela sessão atual
   * @return novo token após a renovação ou vazio diante de perda, expiração ou versão divergente
   * @throws NullPointerException quando o token é nulo
   */
  @Transactional
  public Optional<MaintenanceLeaseVO> renew(MaintenanceLeaseVO expectedLease) {
    Objects.requireNonNull(expectedLease, "expectedLease must not be null");
    MaintenanceSessionVO currentSession = sessionService.getCurrentSession();
    if (!expectedLease.owner().equals(currentSession)) {
      return Optional.empty();
    }

    int affectedRows = repository.renewIfOwned(
        expectedLease.leaseKey(),
        currentSession.instanceId(),
        currentSession.sessionId().toString(),
        expectedLease.epoch(),
        expectedLease.version(),
        leaseTimeoutMicroseconds);
    if (affectedRows != 1) {
      return Optional.empty();
    }

    return repository.findByLeaseKey(expectedLease.leaseKey())
        .map(this::toValue)
        .filter(lease -> lease.owner().equals(currentSession))
        .filter(lease -> lease.epoch() == expectedLease.epoch());
  }

  /**
   * Comprova se o token pertence à sessão atual e já pode iniciar um job ou lote.
   *
   * <p>A prova é sempre relida no banco global e usa o relógio do MySQL para verificar vigência e
   * estabilização. A versão não participa da prova porque um heartbeat concorrente da própria
   * sessão pode renová-la sem alterar o {@code epoch}.
   *
   * @param expectedLease token obtido pela sessão atual
   * @return {@code true} somente quando propriedade, fencing, vigência e estabilização persistidos
   *     continuam válidos
   * @throws NullPointerException quando o token é nulo
   */
  @Transactional(readOnly = true)
  public boolean provesStableOwnership(MaintenanceLeaseVO expectedLease) {
    Objects.requireNonNull(expectedLease, "expectedLease must not be null");
    MaintenanceSessionVO currentSession = sessionService.getCurrentSession();
    if (!expectedLease.owner().equals(currentSession)) {
      return false;
    }
    return repository.countStableOwnership(
        expectedLease.leaseKey(),
        currentSession.instanceId(),
        currentSession.sessionId().toString(),
        expectedLease.epoch(),
        stabilizationMicroseconds) == 1;
  }

  /**
   * Converte a entidade restrita ao backend no token imutável usado pela coordenação.
   *
   * @param entity estado persistido obrigatório
   * @return token completo do lease
   * @throws IllegalArgumentException quando a sessão persistida não é um UUID válido
   */
  private MaintenanceLeaseVO toValue(MaintenanceLeaseEntity entity) {
    MaintenanceSessionVO owner =
        new MaintenanceSessionVO(entity.getInstanceId(), UUID.fromString(entity.getSessionId()));
    return new MaintenanceLeaseVO(
        entity.getLeaseKey(),
        owner,
        entity.getEpoch(),
        entity.getAcquiredAt(),
        entity.getHeartbeatAt(),
        entity.getLeaseUntil(),
        entity.getVersion());
  }

  private static long toMicroseconds(Duration duration) {
    return Math.addExact(
        Math.multiplyExact(duration.getSeconds(), 1_000_000L),
        duration.getNano() / 1_000L);
  }

  /**
   * Protege o contrato físico da chave antes de iniciar uma transação de disputa.
   *
   * @param leaseKey chave a validar
   * @throws IllegalArgumentException quando a chave está vazia ou excede 64 caracteres
   * @throws NullPointerException quando a chave é nula
   */
  private static void validateLeaseKey(String leaseKey) {
    Objects.requireNonNull(leaseKey, "leaseKey must not be null");
    if (leaseKey.isBlank()) {
      throw new IllegalArgumentException("leaseKey must not be blank");
    }
    if (leaseKey.length() > MAXIMUM_LEASE_KEY_LENGTH) {
      throw new IllegalArgumentException("leaseKey must not exceed 64 characters");
    }
  }
}
