package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.RegistrationResendRequestDTO;
import br.com.rinos.app.api.enums.RegistrationResendStatusEnum;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.vo.RegistrationResendResultVO;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationResendService;
import br.com.rinos.app.backend.module.identity.vo.RegistrationResendTransactionVO;

/**
 * Orquestra o reenvio e neutraliza consultas que não correspondem a uma pendência elegível.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationResendFacadeImpl implements RegistrationResendFacade {

  private final IdentityService identityService;
  private final RegistrationResendService resendService;
  private final RegistrationObservabilityService observabilityService;
  private final Clock clock;

  /**
   * Cria a fachada com o relógio UTC da aplicação.
   *
   * @param identityService localização normalizada da pendência
   * @param resendService operação transacional serializada
   */
  @Autowired
  public RegistrationResendFacadeImpl(
      IdentityService identityService,
      RegistrationResendService resendService,
      RegistrationObservabilityService observabilityService) {
    this(
        identityService,
        resendService,
        observabilityService,
        Clock.systemUTC());
  }

  /**
   * Cria a fachada com relógio controlável para testes determinísticos.
   *
   * @param identityService localização normalizada da pendência
   * @param resendService operação transacional serializada
   * @param clock relógio da decisão
   */
  RegistrationResendFacadeImpl(
      IdentityService identityService,
      RegistrationResendService resendService,
      RegistrationObservabilityService observabilityService,
      Clock clock) {
    this.identityService = Objects.requireNonNull(
        identityService,
        "identityService must not be null");
    this.resendService = Objects.requireNonNull(
        resendService,
        "resendService must not be null");
    this.observabilityService = Objects.requireNonNull(
        observabilityService,
        "observabilityService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RegistrationResendResultVO> resend(
      RegistrationResendRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    Instant startedAt = clock.instant();
    return observe(
        resendInternal(request, startedAt),
        request.getCorrelationId(),
        startedAt);
  }

  private CompletionStage<RegistrationResendResultVO> resendInternal(
      RegistrationResendRequestDTO request,
      Instant occurredAt) {
    Optional<RegistrationEntity> registration;
    try {
      registration = identityService.findPendingRegistration(request.getIdentifier());
    } catch (IllegalArgumentException invalidIdentifier) {
      return completed(new RegistrationResendResultVO(
          RegistrationResendStatusEnum.VALIDATION_REJECTED,
          Map.of("identifier", "registration.error.email-invalid"),
          null));
    } catch (RuntimeException unavailable) {
      return completed(RegistrationResendResultVO.of(
          RegistrationResendStatusEnum.UNAVAILABLE));
    }
    if (registration.isEmpty()) {
      return completed(RegistrationResendResultVO.of(
          RegistrationResendStatusEnum.REQUEST_ACCEPTED));
    }

    try {
      RegistrationResendTransactionVO transaction = resendService.resend(
          registration.get().getId(),
          request.getLocale(),
          request.getCorrelationId(),
          occurredAt);
      if (!transaction.eligible()) {
        return completed(RegistrationResendResultVO.of(
            RegistrationResendStatusEnum.REQUEST_ACCEPTED));
      }
      if (transaction.blocked()) {
        Duration retryAfter = Duration.between(occurredAt, transaction.blockedUntil());
        return completed(new RegistrationResendResultVO(
            RegistrationResendStatusEnum.RATE_LIMITED,
            Map.of(),
            retryAfter.isNegative() ? Duration.ZERO : retryAfter));
      }
      return transaction.dispatch().thenApply(dispatch -> dispatch.accepted()
          ? RegistrationResendResultVO.acceptedWithExpiration(transaction.expiresAt())
          : RegistrationResendResultVO.of(
              RegistrationResendStatusEnum.EMAIL_DISPATCH_FAILED));
    } catch (RuntimeException unavailable) {
      return completed(RegistrationResendResultVO.of(
          RegistrationResendStatusEnum.UNAVAILABLE));
    }
  }

  private CompletionStage<RegistrationResendResultVO> observe(
      CompletionStage<RegistrationResendResultVO> result,
      UUID correlationId,
      Instant startedAt) {
    return result.whenComplete((value, failure) -> {
      String resultCode = failure == null && value != null
          ? value.status().name()
          : "UNEXPECTED_FAILURE";
      try {
        observabilityService.recordOperation(
            RegistrationOperationEnum.RESEND,
            resultCode,
            correlationId,
            startedAt,
            clock.instant());
      } catch (RuntimeException ignoredObservabilityFailure) {
        // A telemetria não participa da decisão funcional nem da resposta pública.
      }
    });
  }

  private static CompletionStage<RegistrationResendResultVO> completed(
      RegistrationResendResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
