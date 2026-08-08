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
import org.springframework.web.util.HtmlUtils;

import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.eng.rodrigogml.rfw.exception.RFWInfrastructureException;
import br.eng.rodrigogml.rfw.exception.RFWIntegrationException;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Coordena o despacho direto da comprovação somente depois do commit proprietário.
 *
 * <p>A mensagem completa existe apenas durante o callback pós-commit. Falhas são convertidas
 * em resultados seguros, não revertem a pendência confirmada e nunca iniciam retentativa automática.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Service
@Lazy
public class VerificationEmailDispatchService {

  static final String ATTEMPT_METRIC_NAME =
      "rinos.registration.verification.smtp.attempts";
  static final String DURATION_METRIC_NAME =
      "rinos.registration.verification.smtp.duration";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(VerificationEmailDispatchService.class);

  private final EmailDispatchService emailDispatchService;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  /**
   * Cria o coordenador usando o relógio UTC da aplicação.
   *
   * @param emailDispatchService montagem e transporte oferecidos pelo RFW
   * @param meterRegistry registro de métricas operacionais
   */
  @Autowired
  public VerificationEmailDispatchService(
      EmailDispatchService emailDispatchService,
      MeterRegistry meterRegistry) {
    this(emailDispatchService, meterRegistry, Clock.systemUTC());
  }

  /**
   * Cria uma instância com relógio controlável.
   *
   * @param emailDispatchService montagem e transporte oferecidos pelo RFW
   * @param meterRegistry registro de métricas operacionais
   * @param clock relógio usado nas medições
   */
  VerificationEmailDispatchService(
      EmailDispatchService emailDispatchService,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.emailDispatchService = Objects.requireNonNull(
        emailDispatchService,
        "emailDispatchService must not be null");
    this.meterRegistry = Objects.requireNonNull(
        meterRegistry,
        "meterRegistry must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Registra o despacho na transação ativa e conclui o resultado depois da decisão transacional.
   *
   * @param request dados efêmeros da comprovação
   * @return estágio concluído após aceitação, falha ou rollback
   * @throws IllegalStateException quando chamado fora de transação sincronizada
   */
  public CompletionStage<VerificationEmailDispatchResultVO> scheduleAfterCommit(
      VerificationEmailDispatchRequestVO request) {
    Objects.requireNonNull(request, "request must not be null");
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException(
          "Verification e-mail dispatch requires an active synchronized transaction");
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

  private VerificationEmailDispatchResultVO dispatch(
      VerificationEmailDispatchRequestVO request) {
    Instant startedAt = clock.instant();
    EmailMessage message;
    try {
      message = emailDispatchService.createMessage(
          request.template().getTemplateName(),
          request.locale(),
          null,
          List.of(request.recipient()),
          List.of(),
          List.of(),
          null,
          HtmlUtils.htmlEscape(request.confirmationUrl().toASCIIString()),
          formatExpiry(request.expiresAt(), request.locale()),
          HtmlUtils.htmlEscape(request.manualCode() == null ? "" : request.manualCode()));
    } catch (RFWInfrastructureException | RuntimeException exception) {
      return complete(
          request,
          startedAt,
          VerificationEmailDispatchStatusEnum.TEMPLATE_FAILURE,
          exception);
    }

    try {
      emailDispatchService.dispatch(message);
      return complete(
          request,
          startedAt,
          VerificationEmailDispatchStatusEnum.ACCEPTED,
          null);
    } catch (RFWIntegrationException | RuntimeException exception) {
      return complete(
          request,
          startedAt,
          VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE,
          exception);
    }
  }

  private VerificationEmailDispatchResultVO complete(
      VerificationEmailDispatchRequestVO request,
      Instant startedAt,
      VerificationEmailDispatchStatusEnum status,
      Exception failure) {
    Duration elapsed = Duration.between(startedAt, clock.instant());
    if (elapsed.isNegative()) {
      elapsed = Duration.ZERO;
    }
    String resultTag = status.name().toLowerCase(Locale.ROOT);
    Counter.builder(ATTEMPT_METRIC_NAME)
        .description("Tentativas pós-commit de envio de comprovação")
        .tag("result", resultTag)
        .register(meterRegistry)
        .increment();
    Timer.builder(DURATION_METRIC_NAME)
        .description("Tempo do início pós-commit até a conclusão SMTP")
        .tag("result", resultTag)
        .register(meterRegistry)
        .record(elapsed);
    if (failure == null) {
      LOGGER.info(
          "Despacho SMTP de comprovação concluído: correlationId={}, result={}",
          request.correlationId(),
          resultTag);
    } else {
      LOGGER.warn(
          "Despacho SMTP de comprovação falhou: correlationId={}, result={}, failureType={}",
          request.correlationId(),
          resultTag,
          failure.getClass().getSimpleName());
    }
    return new VerificationEmailDispatchResultVO(
        status,
        request.correlationId(),
        elapsed);
  }

  private static String formatExpiry(Instant expiresAt, Locale locale) {
    Locale effectiveLocale = locale == null ? Locale.getDefault() : locale;
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(effectiveLocale)
        .withZone(ZoneId.of("UTC"))
        .format(expiresAt);
  }
}
