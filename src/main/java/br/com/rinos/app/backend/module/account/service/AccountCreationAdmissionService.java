package br.com.rinos.app.backend.module.account.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.service.OriginLimitService;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.config.AccountCreationPropertiesConfig;

/**
 * Aplica a política específica de criação de contas sobre o contador global de origem.
 *
 * <p>A reserva deve participar da mesma transação que cria uma nova intenção. Assim uma falha
 * posterior não consome cota, enquanto o bloqueio de uma origem permanece durável.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-24
 */
@Service
@Lazy
public class AccountCreationAdmissionService {

  private final OriginLimitService originLimitService;
  private final AccountCreationPropertiesConfig properties;

  /**
   * Cria o serviço com a política fixa da instalação.
   *
   * @param originLimitService contador global de origem
   * @param properties limites exclusivos do cadastro de contas
   */
  public AccountCreationAdmissionService(
      OriginLimitService originLimitService,
      AccountCreationPropertiesConfig properties) {
    this.originLimitService = Objects.requireNonNull(
        originLimitService, "originLimitService must not be null");
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
  }

  /**
   * Decide se a origem já exige prova humana, sem alterar seu contador.
   *
   * @param origin origem normalizada
   * @return {@code true} quando a prova Turnstile é obrigatória
   */
  public boolean requiresHumanVerification(OriginAddressVO origin) {
    return originLimitService.requiresTurnstile(
        origin,
        OriginOperationEnum.ACCOUNT_CREATION,
        properties.turnstileRequiredAfter());
  }

  /**
   * Revalida prova e reserva a próxima criação dentro da transação da intenção.
   *
   * @param origin origem normalizada
   * @param humanVerificationValid {@code true} somente após validação remota positiva
   * @param occurredAt instante usado para calcular a espera segura
   * @return decisão segura de admissão
   */
  public AccountCreationAdmissionResult admit(
      OriginAddressVO origin,
      boolean humanVerificationValid,
      Instant occurredAt) {
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    if (requiresHumanVerification(origin) && !humanVerificationValid) {
      return AccountCreationAdmissionResult.humanVerificationRequired();
    }
    OriginReservationResultVO reservation = originLimitService.reserve(
        origin,
        OriginOperationEnum.ACCOUNT_CREATION,
        properties.originLimit(),
        properties.originWindow(),
        properties.originBlockPeriod());
    if (reservation.status() == OriginReservationStatusEnum.RESERVED) {
      return AccountCreationAdmissionResult.permitted();
    }
    Duration retryAfter = Duration.between(occurredAt, reservation.blockedUntil());
    return AccountCreationAdmissionResult.rateLimited(retryAfter.isNegative()
        ? Duration.ZERO : retryAfter);
  }
}
