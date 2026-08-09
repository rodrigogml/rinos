package br.com.rinos.app.backend.module.identity.facade;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rinos.app.api.dto.EmailOtpEmissionRequestDTO;
import br.com.rinos.app.api.dto.EmailOtpVerificationRequestDTO;
import br.com.rinos.app.api.enums.EmailOtpEmissionStatusEnum;
import br.com.rinos.app.api.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.api.facade.EmailOtpFacade;
import br.com.rinos.app.api.vo.EmailOtpEmissionResultVO;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.service.EmailOtpDispatchService;
import br.com.rinos.app.backend.module.identity.service.EmailOtpService;
import br.com.rinos.app.backend.module.identity.vo.EmailOtpDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.EmailOtpEmissionDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedEmailOtpVO;

/**
 * Mantém a transação da emissão aberta até registrar o callback SMTP pós-commit.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class EmailOtpFacadeImpl implements EmailOtpFacade {

  private final EmailOtpService service;
  private final EmailOtpDispatchService dispatcher;
  private final Clock clock;

  /** Cria a fachada com relógio UTC para compensações posteriores ao commit. */
  @Autowired
  public EmailOtpFacadeImpl(EmailOtpService service, EmailOtpDispatchService dispatcher) {
    this(service, dispatcher, Clock.systemUTC());
  }

  EmailOtpFacadeImpl(EmailOtpService service, EmailOtpDispatchService dispatcher, Clock clock) {
    this.service = Objects.requireNonNull(service);
    this.dispatcher = Objects.requireNonNull(dispatcher);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  @Transactional
  public CompletionStage<EmailOtpEmissionResultVO> begin(EmailOtpEmissionRequestDTO request) {
    return emit(request, false);
  }

  @Override
  @Transactional
  public CompletionStage<EmailOtpEmissionResultVO> resend(EmailOtpEmissionRequestDTO request) {
    return emit(request, true);
  }

  @Override
  public EmailOtpVerificationStatusEnum verify(EmailOtpVerificationRequestDTO request) {
    if (request == null || request.challengeReference() == null
        || request.challengeReference().isBlank() || request.proof() == null
        || request.proof().isBlank() || request.occurredAt() == null) {
      return EmailOtpVerificationStatusEnum.REJECTED;
    }
    try {
      return EmailOtpVerificationStatusEnum.valueOf(service.verify(
          request.challengeReference(), request.proof(), request.occurredAt()).name());
    } catch (RuntimeException unavailable) {
      return EmailOtpVerificationStatusEnum.UNAVAILABLE;
    }
  }

  private CompletionStage<EmailOtpEmissionResultVO> emit(
      EmailOtpEmissionRequestDTO request,
      boolean resend) {
    if (request == null || request.challengeReference() == null
        || request.challengeReference().isBlank() || request.occurredAt() == null) {
      return CompletableFuture.completedFuture(
          EmailOtpEmissionResultVO.terminal(EmailOtpEmissionStatusEnum.REJECTED));
    }
    EmailOtpEmissionDecisionVO decision = service.issue(
        request.challengeReference(), resend, request.occurredAt());
    return switch (decision.status()) {
      case REJECTED -> CompletableFuture.completedFuture(
          EmailOtpEmissionResultVO.terminal(EmailOtpEmissionStatusEnum.REJECTED));
      case RATE_LIMITED -> CompletableFuture.completedFuture(
          EmailOtpEmissionResultVO.rateLimited(decision.retryAfter()));
      case EMITTED -> dispatch(request, decision.issued());
    };
  }

  private CompletionStage<EmailOtpEmissionResultVO> dispatch(
      EmailOtpEmissionRequestDTO request,
      IssuedEmailOtpVO issued) {
    return dispatcher.scheduleAfterCommit(new EmailOtpDispatchRequestVO(
        issued.recipient(), issued.code(), issued.expiresAt(), request.locale(),
        issued.correlationId())).thenApply(result -> {
          if (result.status() == VerificationEmailDispatchStatusEnum.ACCEPTED) {
            return new EmailOtpEmissionResultVO(
                EmailOtpEmissionStatusEnum.EMITTED,
                issued.challengeReference(),
                issued.maskedDestination(),
                issued.expiresAt(),
                issued.resendAvailableAt(),
                null);
          }
          service.invalidateFailedDelivery(
              issued.challengeReference(),
              issued.proofDigest(),
              later(clock.instant(), request.occurredAt()));
          return EmailOtpEmissionResultVO.terminal(EmailOtpEmissionStatusEnum.UNAVAILABLE);
        });
  }

  private static Instant later(Instant first, Instant second) {
    return first.isAfter(second) ? first : second;
  }
}
