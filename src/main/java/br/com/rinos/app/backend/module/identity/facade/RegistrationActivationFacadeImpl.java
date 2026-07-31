package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.service.RegistrationActivationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;

/**
 * Protege o contrato público e delega a ativação à fronteira transacional.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class RegistrationActivationFacadeImpl implements RegistrationActivationFacade {

  private final RegistrationActivationService activationService;
  private final RegistrationObservabilityService observabilityService;
  private final Clock clock;

  /**
   * Cria a fachada com relógio UTC.
   *
   * @param activationService caso de uso transacional
   */
  @Autowired
  public RegistrationActivationFacadeImpl(
      RegistrationActivationService activationService,
      RegistrationObservabilityService observabilityService) {
    this(
        activationService,
        observabilityService,
        Clock.systemUTC());
  }

  RegistrationActivationFacadeImpl(
      RegistrationActivationService activationService,
      RegistrationObservabilityService observabilityService,
      Clock clock) {
    this.activationService = Objects.requireNonNull(
        activationService,
        "activationService must not be null");
    this.observabilityService = Objects.requireNonNull(
        observabilityService,
        "observabilityService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RegistrationActivationResultVO> activate(
      RegistrationActivationRequestDTO request) {
    Instant startedAt = clock.instant();
    UUID correlationId = request == null ? null : request.getCorrelationId();
    return observe(
        activateInternal(request, startedAt),
        RegistrationOperationEnum.ACTIVATE,
        correlationId,
        startedAt);
  }

  private CompletionStage<RegistrationActivationResultVO> activateInternal(
      RegistrationActivationRequestDTO request,
      Instant occurredAt) {
    if (request == null || isBlank(request.getProof()) || request.getCorrelationId() == null) {
      return completed(RegistrationActivationResultVO.validationRejected(
          Map.of("proof", "registration.activation.error.proof-required")));
    }
    try {
      return completed(activationService.activate(
          request.getProof(),
          request.getCorrelationId(),
          occurredAt));
    } catch (RuntimeException unavailable) {
      return completed(RegistrationActivationResultVO.of(
          RegistrationActivationStatusEnum.UNAVAILABLE));
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RegistrationActivationResultVO> completeConsent(
      ActivationConsentRequestDTO request) {
    Instant startedAt = clock.instant();
    UUID correlationId = request == null ? null : request.getCorrelationId();
    return observe(
        completeConsentInternal(request, startedAt),
        RegistrationOperationEnum.ACTIVATION_CONSENT,
        correlationId,
        startedAt);
  }

  private CompletionStage<RegistrationActivationResultVO> completeConsentInternal(
      ActivationConsentRequestDTO request,
      Instant occurredAt) {
    if (request == null || isBlank(request.getActivationReference())
        || request.getCorrelationId() == null) {
      return completed(RegistrationActivationResultVO.validationRejected(
          Map.of("activationReference",
              "registration.activation.error.proof-required")));
    }
    List<Long> acceptedIds;
    try {
      acceptedIds = request.getAcceptedLegalDocumentIds().stream()
          .map(Long::valueOf)
          .toList();
    } catch (RuntimeException invalidDocument) {
      return completed(RegistrationActivationResultVO.validationRejected(
          Map.of("acceptedLegalDocumentIds",
              "registration.error.legal-documents")));
    }

    try {
      return completed(activationService.completeConsent(
          request.getActivationReference(),
          acceptedIds,
          request.getCorrelationId(),
          occurredAt));
    } catch (IllegalArgumentException staleDocuments) {
      return completed(RegistrationActivationResultVO.validationRejected(
          Map.of("acceptedLegalDocumentIds",
              "registration.error.legal-documents")));
    } catch (RuntimeException unavailable) {
      return completed(RegistrationActivationResultVO.of(
          RegistrationActivationStatusEnum.UNAVAILABLE));
    }
  }

  private CompletionStage<RegistrationActivationResultVO> observe(
      CompletionStage<RegistrationActivationResultVO> result,
      RegistrationOperationEnum operation,
      UUID correlationId,
      Instant startedAt) {
    return result.whenComplete((value, failure) -> {
      String resultCode = failure == null && value != null
          ? value.status().name()
          : "UNEXPECTED_FAILURE";
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
    });
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static CompletionStage<RegistrationActivationResultVO> completed(
      RegistrationActivationResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
