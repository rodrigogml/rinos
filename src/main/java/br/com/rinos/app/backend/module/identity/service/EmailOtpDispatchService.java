package br.com.rinos.app.backend.module.identity.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailTemplateEnum;
import br.com.rinos.app.backend.module.identity.vo.EmailOtpDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.eng.rodrigogml.rfw.exception.RFWInfrastructureException;
import br.eng.rodrigogml.rfw.exception.RFWIntegrationException;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Entrega o OTP por e-mail somente no callback posterior ao commit da prova.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class EmailOtpDispatchService {

  static final String ATTEMPT_METRIC_NAME = "rinos.authentication.email_otp.smtp.attempts";
  static final String DURATION_METRIC_NAME = "rinos.authentication.email_otp.smtp.duration";
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailOtpDispatchService.class);

  private final EmailDispatchService emailDispatchService;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  /** Cria o dispatcher com relógio UTC. */
  @Autowired
  public EmailOtpDispatchService(
      EmailDispatchService emailDispatchService,
      MeterRegistry meterRegistry) {
    this(emailDispatchService, meterRegistry, Clock.systemUTC());
  }

  EmailOtpDispatchService(
      EmailDispatchService emailDispatchService,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.emailDispatchService = Objects.requireNonNull(emailDispatchService);
    this.meterRegistry = Objects.requireNonNull(meterRegistry);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * Agenda a entrega na transação ativa e conclui o estágio após SMTP ou rollback.
   *
   * @throws IllegalStateException quando não existe transação sincronizada
   */
  public CompletionStage<VerificationEmailDispatchResultVO> scheduleAfterCommit(
      EmailOtpDispatchRequestVO request) {
    Objects.requireNonNull(request, "request must not be null");
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("E-mail OTP dispatch requires an active transaction");
    }
    CompletableFuture<VerificationEmailDispatchResultVO> result = new CompletableFuture<>();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        result.complete(dispatch(request));
      }

      @Override
      public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED && !result.isDone()) {
          result.complete(new VerificationEmailDispatchResultVO(
              VerificationEmailDispatchStatusEnum.TRANSACTION_ROLLED_BACK,
              request.correlationId(),
              Duration.ZERO));
        }
      }
    });
    return result;
  }

  private VerificationEmailDispatchResultVO dispatch(EmailOtpDispatchRequestVO request) {
    Instant startedAt = clock.instant();
    EmailMessage message;
    try {
      message = emailDispatchService.createMessage(
          VerificationEmailTemplateEnum.AUTHENTICATION_EMAIL_CODE.getTemplateName(),
          request.locale(),
          null,
          List.of(request.recipient()),
          List.of(),
          List.of(),
          null,
          request.code(),
          formatExpiry(request.expiresAt(), request.locale()));
    } catch (RFWInfrastructureException | RuntimeException failure) {
      return complete(request, startedAt,
          VerificationEmailDispatchStatusEnum.TEMPLATE_FAILURE, failure);
    }
    try {
      emailDispatchService.dispatch(message);
      return complete(request, startedAt, VerificationEmailDispatchStatusEnum.ACCEPTED, null);
    } catch (RFWIntegrationException | RuntimeException failure) {
      return complete(request, startedAt,
          VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE, failure);
    }
  }

  private VerificationEmailDispatchResultVO complete(
      EmailOtpDispatchRequestVO request,
      Instant startedAt,
      VerificationEmailDispatchStatusEnum status,
      Exception failure) {
    Duration elapsed = Duration.between(startedAt, clock.instant());
    if (elapsed.isNegative()) {
      elapsed = Duration.ZERO;
    }
    String result = status.name().toLowerCase(Locale.ROOT);
    Counter.builder(ATTEMPT_METRIC_NAME).tag("result", result)
        .register(meterRegistry).increment();
    Timer.builder(DURATION_METRIC_NAME).tag("result", result)
        .register(meterRegistry).record(elapsed);
    if (failure == null) {
      LOGGER.info("Despacho SMTP de OTP concluído: correlationId={}, result={}",
          request.correlationId(), result);
    } else {
      LOGGER.warn("Despacho SMTP de OTP falhou: correlationId={}, result={}, failureType={}",
          request.correlationId(), result, failure.getClass().getSimpleName());
    }
    return new VerificationEmailDispatchResultVO(status, request.correlationId(), elapsed);
  }

  private static String formatExpiry(Instant expiresAt, Locale locale) {
    Locale effective = locale == null ? Locale.getDefault() : locale;
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(effective)
        .withZone(ZoneId.of("UTC"))
        .format(expiresAt);
  }
}
