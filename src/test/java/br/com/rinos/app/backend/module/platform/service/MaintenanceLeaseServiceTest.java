package br.com.rinos.app.backend.module.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.rinos.app.backend.module.platform.entity.MaintenanceLeaseEntity;
import br.com.rinos.app.backend.module.platform.repository.MaintenanceLeaseRepository;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceLeaseVO;
import br.com.rinos.app.backend.module.platform.vo.MaintenanceSessionVO;
import br.com.rinos.app.config.MaintenancePropertiesConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("Coordenação do lease de manutenção")
class MaintenanceLeaseServiceTest {

  private static final String LEASE_KEY = "global-maintenance";
  private static final String INSTANCE_ID = "instance-one";
  private static final long LEASE_TIMEOUT_MICROSECONDS = 14_400_000_000L;
  private static final Instant ACQUIRED_AT = Instant.parse("2026-07-29T10:00:00Z");
  private static final Instant HEARTBEAT_AT = Instant.parse("2026-07-29T10:30:00Z");
  private static final Instant LEASE_UNTIL = Instant.parse("2026-07-29T14:30:00Z");

  @Mock
  private MaintenanceLeaseRepository repository;

  @Mock
  private MaintenanceSessionService sessionService;

  private MaintenanceSessionVO currentSession;
  private MaintenanceLeaseService service;

  /**
   * Prepara uma sessão fixa e o serviço com o timeout padrão de quatro horas.
   */
  @BeforeEach
  void setUp() {
    currentSession = new MaintenanceSessionVO(
        INSTANCE_ID,
        UUID.fromString("65cb579b-1e02-4a89-83c6-f9a8af8f83ea"));
    when(sessionService.getCurrentSession()).thenReturn(currentSession);
    service = new MaintenanceLeaseService(repository, sessionService, properties());
  }

  /**
   * Comprova que a sessão retorna o token somente depois de persistir e verificar a propriedade.
   */
  @Test
  void tryAcquire_shouldReturnLease_whenCurrentSessionWins() {
    MaintenanceLeaseEntity entity = leaseEntity(currentSession, 1, 0);
    when(repository.findByLeaseKey(LEASE_KEY)).thenReturn(Optional.of(entity));

    Optional<MaintenanceLeaseVO> result = service.tryAcquire(LEASE_KEY);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().owner()).isEqualTo(currentSession);
    assertThat(result.orElseThrow().epoch()).isEqualTo(1);
    verify(repository).createIfAbsent(
        LEASE_KEY,
        INSTANCE_ID,
        currentSession.sessionId().toString(),
        LEASE_TIMEOUT_MICROSECONDS);
    verify(repository).takeOverIfExpired(
        LEASE_KEY,
        INSTANCE_ID,
        currentSession.sessionId().toString(),
        LEASE_TIMEOUT_MICROSECONDS);
  }

  /**
   * Comprova que a leitura de outro proprietário não declara vitória local.
   */
  @Test
  void tryAcquire_shouldReturnEmpty_whenAnotherSessionOwnsLease() {
    MaintenanceSessionVO otherSession = new MaintenanceSessionVO(
        "instance-two",
        UUID.fromString("88c54ec1-12f6-49a7-b267-57d643a5d8fd"));
    MaintenanceLeaseEntity entity = leaseEntity(otherSession, 1, 0);
    when(repository.findByLeaseKey(LEASE_KEY)).thenReturn(Optional.of(entity));

    Optional<MaintenanceLeaseVO> result = service.tryAcquire(LEASE_KEY);

    assertThat(result).isEmpty();
  }

  /**
   * Comprova que uma renovação aceita retorna a nova versão persistida.
   */
  @Test
  void renew_shouldReturnUpdatedLease_whenAllOwnershipTokensMatch() {
    MaintenanceLeaseVO expected = leaseValue(currentSession, 3, 4);
    when(repository.renewIfOwned(
        LEASE_KEY,
        INSTANCE_ID,
        currentSession.sessionId().toString(),
        3,
        4,
        LEASE_TIMEOUT_MICROSECONDS)).thenReturn(1);
    MaintenanceLeaseEntity renewedEntity = leaseEntity(currentSession, 3, 5);
    when(repository.findByLeaseKey(LEASE_KEY)).thenReturn(Optional.of(renewedEntity));

    Optional<MaintenanceLeaseVO> result = service.renew(expected);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().epoch()).isEqualTo(3);
    assertThat(result.orElseThrow().version()).isEqualTo(5);
  }

  /**
   * Comprova que token de outra sessão nem sequer dispara uma mutação.
   */
  @Test
  void renew_shouldReturnEmpty_whenTokenBelongsToAnotherSession() {
    MaintenanceSessionVO otherSession = new MaintenanceSessionVO(
        "instance-two",
        UUID.fromString("88c54ec1-12f6-49a7-b267-57d643a5d8fd"));

    Optional<MaintenanceLeaseVO> result = service.renew(leaseValue(otherSession, 3, 4));

    assertThat(result).isEmpty();
    verify(repository, never()).renewIfOwned(
        LEASE_KEY,
        INSTANCE_ID,
        currentSession.sessionId().toString(),
        3,
        4,
        LEASE_TIMEOUT_MICROSECONDS);
  }

  /**
   * Comprova que divergência de versão ou expiração é tratada como perda da renovação.
   */
  @Test
  void renew_shouldReturnEmpty_whenConditionalUpdateRejectsToken() {
    MaintenanceLeaseVO expected = leaseValue(currentSession, 3, 4);
    when(repository.renewIfOwned(
        LEASE_KEY,
        INSTANCE_ID,
        currentSession.sessionId().toString(),
        3,
        4,
        LEASE_TIMEOUT_MICROSECONDS)).thenReturn(0);

    Optional<MaintenanceLeaseVO> result = service.renew(expected);

    assertThat(result).isEmpty();
    verify(repository, never()).findByLeaseKey(LEASE_KEY);
  }

  /**
   * Cria uma entidade simulada com o contrato completo de persistência.
   *
   * @param owner sessão proprietária
   * @param epoch fencing token
   * @param version versão otimista
   * @return entidade simulada
   */
  private MaintenanceLeaseEntity leaseEntity(
      MaintenanceSessionVO owner,
      long epoch,
      long version) {
    MaintenanceLeaseEntity entity = mock(MaintenanceLeaseEntity.class);
    when(entity.getLeaseKey()).thenReturn(LEASE_KEY);
    when(entity.getInstanceId()).thenReturn(owner.instanceId());
    when(entity.getSessionId()).thenReturn(owner.sessionId().toString());
    when(entity.getEpoch()).thenReturn(epoch);
    when(entity.getAcquiredAt()).thenReturn(ACQUIRED_AT);
    when(entity.getHeartbeatAt()).thenReturn(HEARTBEAT_AT);
    when(entity.getLeaseUntil()).thenReturn(LEASE_UNTIL);
    when(entity.getVersion()).thenReturn(version);
    return entity;
  }

  /**
   * Cria um token de entrada com proprietário e fencing controlados pelo cenário.
   *
   * @param owner sessão proprietária
   * @param epoch fencing token
   * @param version versão otimista
   * @return token imutável do lease
   */
  private static MaintenanceLeaseVO leaseValue(
      MaintenanceSessionVO owner,
      long epoch,
      long version) {
    return new MaintenanceLeaseVO(
        LEASE_KEY,
        owner,
        epoch,
        ACQUIRED_AT,
        HEARTBEAT_AT,
        LEASE_UNTIL,
        version);
  }

  /**
   * Cria as propriedades válidas usadas pelo serviço sob teste.
   *
   * @return propriedades com timeout de quatro horas
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
