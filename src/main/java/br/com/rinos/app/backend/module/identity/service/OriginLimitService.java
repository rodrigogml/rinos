package br.com.rinos.app.backend.module.identity.service;

import java.time.Duration;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.backend.module.identity.entity.OriginWindowEntity;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginPolicyEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.OriginWindowRepository;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.config.OriginPropertiesConfig;

/**
 * Avalia Turnstile e reserva atomicamente novas pendências no contador canônico da origem.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class OriginLimitService {

  private static final OriginPolicyEnum COUNTER_POLICY = OriginPolicyEnum.ABSOLUTE_LIMIT;

  private final OriginWindowRepository repository;
  private final OriginPropertiesConfig properties;
  private final long windowMicroseconds;

  /**
   * Cria o serviço a partir das definições fixas da instalação.
   *
   * @param repository persistência global
   * @param properties limiar, limite e janela
   */
  public OriginLimitService(
      OriginWindowRepository repository,
      OriginPropertiesConfig properties) {
    this.repository = repository;
    this.properties = properties;
    windowMicroseconds = toMicroseconds(properties.window());
  }

  /**
   * Indica se a próxima criação exige prova humana sem alterar o contador.
   *
   * @param origin origem binária validada
   * @param operation operação protegida
   * @return {@code true} quando o limiar configurado foi alcançado
   */
  @Transactional(readOnly = true)
  public boolean requiresTurnstile(
      OriginAddressVO origin,
      OriginOperationEnum operation) {
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    if (properties.turnstileThreshold() == 0) {
      return true;
    }
    int currentCount = repository.findCurrent(
        origin.getAddress(),
        operation,
        COUNTER_POLICY)
        .map(OriginWindowEntity::getEventCount)
        .orElse(0);
    return currentCount >= properties.turnstileThreshold();
  }

  /**
   * Reserva uma criação efetiva e nunca permite que o contador ultrapasse o limite.
   *
   * <p>O chamador deve executar esta operação na mesma transação que persiste a nova pendência.
   * A primeira tentativa de inserção precede a busca de expirados para evitar gap locks concorrentes
   * quando ainda não existe janela; a segunda cria a sucessora quando uma janela expirada foi fechada.
   *
   * @param origin origem binária validada
   * @param operation operação protegida
   * @return reserva confirmada ou bloqueio até o fim da janela
   */
  @Transactional
  public OriginReservationResultVO reserveNewRegistration(
      OriginAddressVO origin,
      OriginOperationEnum operation) {
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
    byte[] address = origin.getAddress();
    repository.createActiveIfAbsent(
        address,
        operation.name(),
        COUNTER_POLICY.name(),
        windowMicroseconds);
    repository.closeExpired(address, operation.name(), COUNTER_POLICY.name());
    repository.createActiveIfAbsent(
        address,
        operation.name(),
        COUNTER_POLICY.name(),
        windowMicroseconds);
    int incremented = repository.incrementBelowLimit(
        address,
        operation.name(),
        COUNTER_POLICY.name(),
        properties.absoluteLimit());
    if (incremented == 1) {
      return new OriginReservationResultVO(OriginReservationStatusEnum.RESERVED, null);
    }
    OriginWindowEntity active = repository.findCurrent(address, operation, COUNTER_POLICY)
        .orElseThrow(() -> new IllegalStateException(
            "active origin window disappeared during reservation"));
    return new OriginReservationResultVO(
        OriginReservationStatusEnum.BLOCKED,
        active.getWindowEndsAt());
  }

  private static long toMicroseconds(Duration duration) {
    return Math.addExact(
        Math.multiplyExact(duration.getSeconds(), 1_000_000L),
        duration.getNano() / 1_000L);
  }
}
