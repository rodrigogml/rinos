package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.dto.ExternalRegistrationCompletionRequestDTO;
import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.backend.module.identity.service.ExternalRegistrationCompletionService;

/**
 * Valida a borda pública da conclusão externa e isola falhas persistentes.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class ExternalRegistrationFacadeImpl implements ExternalRegistrationFacade {

  private final ExternalRegistrationCompletionService completionService;
  private final Clock clock;

  /**
   * Cria a fachada com relógio UTC.
   *
   * @param completionService caso de uso transacional
   */
  public ExternalRegistrationFacadeImpl(
      ExternalRegistrationCompletionService completionService) {
    this(completionService, Clock.systemUTC());
  }

  ExternalRegistrationFacadeImpl(
      ExternalRegistrationCompletionService completionService,
      Clock clock) {
    this.completionService = Objects.requireNonNull(
        completionService,
        "completionService must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<ExternalRegistrationCompletionResultVO> complete(
      ExternalRegistrationCompletionRequestDTO request) {
    if (request == null
        || request.registrationReference() == null
        || request.registrationReference().isBlank()
        || request.correlationId() == null) {
      return completed(ExternalRegistrationCompletionResultVO.validationRejected(
          Map.of(
              "registrationReference",
              "registration.google.completion.reference-required")));
    }
    List<Long> acceptedDocumentIds;
    try {
      acceptedDocumentIds = request.acceptedLegalDocumentIds().stream()
          .map(Long::valueOf)
          .toList();
    } catch (RuntimeException invalidDocument) {
      return completed(ExternalRegistrationCompletionResultVO.validationRejected(
          Map.of(
              "acceptedLegalDocumentIds",
              "registration.error.legal-documents")));
    }

    Instant occurredAt = clock.instant();
    try {
      return completed(completionService.complete(
          request.registrationReference(),
          acceptedDocumentIds,
          request.correlationId(),
          occurredAt));
    } catch (IllegalArgumentException invalidLegalDocuments) {
      return completed(ExternalRegistrationCompletionResultVO.validationRejected(
          Map.of(
              "acceptedLegalDocumentIds",
              "registration.error.legal-documents")));
    } catch (DataIntegrityViolationException collision) {
      return completed(ExternalRegistrationCompletionResultVO.of(
          ExternalRegistrationCompletionStatusEnum.CONFLICT));
    } catch (RuntimeException unavailable) {
      return completed(ExternalRegistrationCompletionResultVO.of(
          ExternalRegistrationCompletionStatusEnum.UNAVAILABLE));
    }
  }

  private static CompletionStage<ExternalRegistrationCompletionResultVO> completed(
      ExternalRegistrationCompletionResultVO result) {
    return CompletableFuture.completedFuture(result);
  }
}
