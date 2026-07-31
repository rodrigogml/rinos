package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.api.enums.RegistrationCancellationRequestStatusEnum;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCancellationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCancellationIssueVO;
import br.com.rinos.app.config.VerificationPropertiesConfig;

/**
 * Preserva neutralidade na solicitação e publica somente resultados seguros da confirmação.
 *
 * <p>Cadastros existentes, ausentes ou inelegíveis recebem a mesma continuação aleatória.
 * Falhas de emissão ou SMTP são observáveis internamente, mas não se tornam oráculo de existência.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationCancellationFacadeImpl implements RegistrationCancellationFacade {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(RegistrationCancellationFacadeImpl.class);

  private final IdentityService identityService;
  private final RegistrationCancellationService cancellationService;
  private final VerificationPropertiesConfig verificationProperties;
  private final RegistrationObservabilityService observabilityService;
  private final Clock clock;

  /**
   * Cria a fachada com o relógio UTC da aplicação.
   *
   * @param identityService localização normalizada da pendência
   * @param cancellationService emissão e confirmação transacionais
   * @param verificationProperties validade usada também pela continuação neutra
   */
  @Autowired
  public RegistrationCancellationFacadeImpl(
      IdentityService identityService,
      RegistrationCancellationService cancellationService,
      VerificationPropertiesConfig verificationProperties,
      RegistrationObservabilityService observabilityService) {
    this(
        identityService,
        cancellationService,
        verificationProperties,
        observabilityService,
        Clock.systemUTC());
  }

  /**
   * Cria a fachada com relógio controlável para testes determinísticos.
   *
   * @param identityService localização normalizada da pendência
   * @param cancellationService emissão e confirmação transacionais
   * @param verificationProperties validade configurada
   * @param clock relógio da decisão
   */
  RegistrationCancellationFacadeImpl(
      IdentityService identityService,
      RegistrationCancellationService cancellationService,
      VerificationPropertiesConfig verificationProperties,
      RegistrationObservabilityService observabilityService,
      Clock clock) {
    this.identityService = Objects.requireNonNull(
        identityService,
        "identityService must not be null");
    this.cancellationService = Objects.requireNonNull(
        cancellationService,
        "cancellationService must not be null");
    this.verificationProperties = Objects.requireNonNull(
        verificationProperties,
        "verificationProperties must not be null");
    this.observabilityService = Objects.requireNonNull(
        observabilityService,
        "observabilityService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RegistrationCancellationRequestResultVO> requestCancellation(
      RegistrationCancellationRequestDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    Instant startedAt = clock.instant();
    return observeRequest(
        requestCancellationInternal(request, startedAt),
        request.correlationId(),
        startedAt);
  }

  private CompletionStage<RegistrationCancellationRequestResultVO>
      requestCancellationInternal(
          RegistrationCancellationRequestDTO request,
          Instant occurredAt) {
    RegistrationCancellationRequestResultVO neutral = neutralResult(occurredAt);
    Optional<RegistrationEntity> registration;
    try {
      registration = identityService.findPendingRegistration(request.identifier());
    } catch (IllegalArgumentException invalidIdentifier) {
      return completed(new RegistrationCancellationRequestResultVO(
          RegistrationCancellationRequestStatusEnum.VALIDATION_REJECTED,
          null,
          null,
          Map.of("identifier", "registration.error.email-invalid")));
    } catch (RuntimeException unavailable) {
      logNeutralFailure(request.correlationId(), unavailable);
      return completed(neutral);
    }
    if (registration.isEmpty()) {
      return completed(neutral);
    }

    try {
      RegistrationCancellationIssueVO issue = cancellationService.issue(
          registration.get().getId(),
          request.locale(),
          request.correlationId(),
          occurredAt);
      if (!issue.issued()) {
        return completed(neutral);
      }
      return issue.dispatch().handle((ignored, failure) -> {
        if (failure != null) {
          logNeutralFailure(request.correlationId(), failure);
        }
        return neutral;
      });
    } catch (RuntimeException unavailable) {
      logNeutralFailure(request.correlationId(), unavailable);
      return completed(neutral);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RegistrationCancellationConfirmationResultVO> confirmCancellation(
      RegistrationCancellationConfirmationDTO request) {
    Objects.requireNonNull(request, "request must not be null");
    Instant startedAt = clock.instant();
    return observeConfirmation(
        confirmCancellationInternal(request, startedAt),
        request.correlationId(),
        startedAt);
  }

  private CompletionStage<RegistrationCancellationConfirmationResultVO>
      confirmCancellationInternal(
          RegistrationCancellationConfirmationDTO request,
          Instant occurredAt) {
    try {
      RegistrationCancellationConfirmationStatusEnum status = cancellationService.confirm(
          request.identifier(),
          request.proof(),
          request.correlationId(),
          occurredAt);
      return completed(RegistrationCancellationConfirmationResultVO.of(status));
    } catch (IllegalArgumentException invalidInput) {
      return completed(new RegistrationCancellationConfirmationResultVO(
          RegistrationCancellationConfirmationStatusEnum.VALIDATION_REJECTED,
          Map.of("identifier", "registration.error.email-invalid")));
    } catch (RuntimeException unavailable) {
      return completed(RegistrationCancellationConfirmationResultVO.of(
          RegistrationCancellationConfirmationStatusEnum.UNAVAILABLE));
    }
  }

  private CompletionStage<RegistrationCancellationRequestResultVO> observeRequest(
      CompletionStage<RegistrationCancellationRequestResultVO> result,
      UUID correlationId,
      Instant startedAt) {
    return result.whenComplete((value, failure) -> recordObservation(
        RegistrationOperationEnum.CANCELLATION_REQUEST,
        failure == null && value != null
            ? value.status().name()
            : "UNEXPECTED_FAILURE",
        correlationId,
        startedAt));
  }

  private CompletionStage<RegistrationCancellationConfirmationResultVO>
      observeConfirmation(
          CompletionStage<RegistrationCancellationConfirmationResultVO> result,
          UUID correlationId,
          Instant startedAt) {
    return result.whenComplete((value, failure) -> recordObservation(
        RegistrationOperationEnum.CANCELLATION_CONFIRM,
        failure == null && value != null
            ? value.status().name()
            : "UNEXPECTED_FAILURE",
        correlationId,
        startedAt));
  }

  private void recordObservation(
      RegistrationOperationEnum operation,
      String resultCode,
      UUID correlationId,
      Instant startedAt) {
    try {
      observabilityService.recordOperation(
          operation,
          resultCode,
          correlationId,
          startedAt,
          clock.instant());
    } catch (RuntimeException ignoredObservabilityFailure) {
      // A telemetria não participa da decisão funcional nem da resposta pública.
    }
  }

  private RegistrationCancellationRequestResultVO neutralResult(Instant occurredAt) {
    return new RegistrationCancellationRequestResultVO(
        RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED,
        UUID.randomUUID().toString(),
        occurredAt.plus(verificationProperties.validity()),
        Map.of());
  }

  private static <T> CompletionStage<T> completed(T result) {
    return CompletableFuture.completedFuture(result);
  }

  private static void logNeutralFailure(UUID correlationId, Throwable failure) {
    LOGGER.warn(
        "Solicitação neutra de cancelamento não pôde despachar prova: "
            + "correlationId={}, failureType={}",
        correlationId,
        failure.getClass().getSimpleName());
  }
}
