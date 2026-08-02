package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailTemplateEnum;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.eng.rodrigogml.rfw.exception.RFWExceptionCodeDefinitions;
import br.eng.rodrigogml.rfw.exception.RFWInfrastructureException;
import br.eng.rodrigogml.rfw.exception.RFWIntegrationException;
import br.eng.rodrigogml.rfw.mail.ClasspathEmailTemplateResolver;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailDispatcher;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import br.eng.rodrigogml.rfw.mail.config.EmailTemplatePropertiesConfig;
import br.eng.rodrigogml.rfw.mail.PositionalEmailTemplateRenderer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Despacho pós-commit da comprovação")
@ExtendWith(OutputCaptureExtension.class)
class VerificationEmailDispatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  @AfterEach
  void clearTransactionState() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void scheduleAfterCommit_shouldRenderAndDispatchOnlyAfterCommit() {
    AtomicReference<EmailMessage> dispatched = new AtomicReference<>();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    VerificationEmailDispatchService service = service(dispatched::set, meterRegistry);

    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(request());

    assertThat(stage.toCompletableFuture()).isNotDone();
    assertThat(dispatched).hasValue(null);

    commitTransaction();

    VerificationEmailDispatchResultVO result = stage.toCompletableFuture().join();
    assertThat(result.status()).isEqualTo(VerificationEmailDispatchStatusEnum.ACCEPTED);
    assertThat(result.accepted()).isTrue();
    assertThat(result.resendAvailable()).isFalse();
    assertThat(dispatched.get().toAddresses()).containsExactly("person@example.test");
    assertThat(dispatched.get().body())
        .contains("https://rinos.test/login?step=activation&amp;proof=secret-token")
        .contains("<code>secret-token</code>")
        .contains("29");
    assertThat(meterRegistry.counter(
        VerificationEmailDispatchService.ATTEMPT_METRIC_NAME,
        "result",
        "accepted").count()).isEqualTo(1);
    assertThat(meterRegistry.timer(
        VerificationEmailDispatchService.DURATION_METRIC_NAME,
        "result",
        "accepted").count()).isEqualTo(1);
  }

  @Test
  void scheduleAfterCommit_shouldRenderCancellationTemplate_whenRequested() {
    AtomicReference<EmailMessage> dispatched = new AtomicReference<>();
    VerificationEmailDispatchService service =
        service(dispatched::set, new SimpleMeterRegistry());

    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(new VerificationEmailDispatchRequestVO(
            "person@example.test",
            URI.create("https://rinos.test/cancel-registration?token=secret-token"),
            null,
            NOW.plusSeconds(3600),
            Locale.of("pt", "BR"),
            UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab"),
            VerificationEmailTemplateEnum.REGISTRATION_CANCELLATION));
    commitTransaction();

    assertThat(stage.toCompletableFuture().join().accepted()).isTrue();
    assertThat(dispatched.get().subject()).contains("cancelamento");
    assertThat(dispatched.get().body())
        .contains("Confirmar cancelamento")
        .contains("/cancel-registration?token=secret-token");
  }

  @Test
  void scheduleAfterCommit_shouldNotDispatch_whenTransactionRollsBack() {
    AtomicInteger attempts = new AtomicInteger();
    VerificationEmailDispatchService service =
        service(ignored -> attempts.incrementAndGet(), new SimpleMeterRegistry());

    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(request());
    rollbackTransaction();

    VerificationEmailDispatchResultVO result = stage.toCompletableFuture().join();
    assertThat(result.status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.TRANSACTION_ROLLED_BACK);
    assertThat(attempts).hasValue(0);
  }

  @Test
  void scheduleAfterCommit_shouldExposeTemplateFailure_withoutTransportAttempt() {
    AtomicInteger attempts = new AtomicInteger();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    EmailDispatchService emailDispatchService = new EmailDispatchService(
        (templateName, locale) -> {
          throw new RFWInfrastructureException(
              RFWExceptionCodeDefinitions.EMAIL_TEMPLATE_NOT_FOUND,
              "template unavailable");
        },
        new PositionalEmailTemplateRenderer(),
        ignored -> attempts.incrementAndGet());
    VerificationEmailDispatchService service = new VerificationEmailDispatchService(
        emailDispatchService,
        meterRegistry,
        Clock.fixed(NOW, ZoneOffset.UTC));

    VerificationEmailDispatchResultVO result = executeCommitted(service);

    assertThat(result.status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.TEMPLATE_FAILURE);
    assertThat(result.resendAvailable()).isTrue();
    assertThat(attempts).hasValue(0);
    assertFailureMetrics(meterRegistry, "template_failure");
  }

  @Test
  void scheduleAfterCommit_shouldExposeTransportFailure_withoutAutomaticRetry() {
    AtomicInteger attempts = new AtomicInteger();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    EmailDispatcher failingDispatcher = ignored -> {
      attempts.incrementAndGet();
      throw new RFWIntegrationException(
          RFWExceptionCodeDefinitions.SMTP_DISPATCH_FAILED,
          "SMTP timeout");
    };
    VerificationEmailDispatchService service =
        service(failingDispatcher, meterRegistry);

    VerificationEmailDispatchResultVO result = executeCommitted(service);

    assertThat(result.status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE);
    assertThat(result.resendAvailable()).isTrue();
    assertThat(attempts).hasValue(1);
    assertFailureMetrics(meterRegistry, "transport_failure");
  }

  @Test
  void scheduleAfterCommit_shouldNeverLogOrTagRequestSecrets(
      CapturedOutput output) {
    String sensitiveFailure = String.join(
        " ",
        "person@example.test",
        "203.0.113.10",
        "Password!123",
        "secret-token",
        "opaque-proof",
        "https://rinos.test/login?step=activation&proof=secret-token");
    EmailDispatcher failingDispatcher = ignored -> {
      throw new RFWIntegrationException(
          RFWExceptionCodeDefinitions.SMTP_DISPATCH_FAILED,
          sensitiveFailure);
    };
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    VerificationEmailDispatchService service =
        service(failingDispatcher, meterRegistry);

    executeCommitted(service);

    assertThat(output.getOut())
        .doesNotContain(
            "person@example.test",
            "203.0.113.10",
            "Password!123",
            "secret-token",
            "opaque-proof",
            "https://rinos.test");
    assertThat(meterRegistry.getMeters())
        .flatExtracting(meter -> meter.getId().getTags())
        .extracting(io.micrometer.core.instrument.Tag::getValue)
        .containsOnly("transport_failure");
  }

  @Test
  void scheduleAfterCommit_shouldAllowExplicitResend_asANewAttempt() {
    AtomicInteger attempts = new AtomicInteger();
    EmailDispatcher dispatcher = ignored -> {
      if (attempts.incrementAndGet() == 1) {
        throw new RFWIntegrationException(
            RFWExceptionCodeDefinitions.SMTP_DISPATCH_FAILED,
            "first attempt failed");
      }
    };
    VerificationEmailDispatchService service =
        service(dispatcher, new SimpleMeterRegistry());

    VerificationEmailDispatchResultVO first = executeCommitted(service);
    VerificationEmailDispatchResultVO second = executeCommitted(service);

    assertThat(first.status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE);
    assertThat(second.status()).isEqualTo(VerificationEmailDispatchStatusEnum.ACCEPTED);
    assertThat(attempts).hasValue(2);
  }

  @Test
  void scheduleAfterCommit_shouldRejectCallOutsideTransaction() {
    VerificationEmailDispatchService service =
        service(ignored -> { }, new SimpleMeterRegistry());

    assertThatThrownBy(() -> service.scheduleAfterCommit(request()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active synchronized transaction");
  }

  @Test
  void request_shouldRejectManualCodeThatCouldInjectMarkup() {
    assertThatThrownBy(() -> new VerificationEmailDispatchRequestVO(
        "person@example.test",
        URI.create("https://rinos.test/login?step=activation&proof=secret-token"),
        "<script>",
        NOW.plusSeconds(3600),
        Locale.of("pt", "BR"),
        UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("URL-safe opaque");
  }

  @Test
  void request_shouldRedactRecipientUrlAndManualCodeFromTextRepresentation() {
    assertThat(request().toString())
        .contains(
            "recipient=REDACTED",
            "confirmationUrl=REDACTED",
            "manualCode=REDACTED")
        .doesNotContain(
            "person@example.test",
            "https://rinos.test",
            "secret-token");
  }

  private static VerificationEmailDispatchService service(
      EmailDispatcher dispatcher,
      SimpleMeterRegistry meterRegistry) {
    EmailTemplatePropertiesConfig properties = new EmailTemplatePropertiesConfig();
    EmailDispatchService emailDispatchService = new EmailDispatchService(
        new ClasspathEmailTemplateResolver(new DefaultResourceLoader(), properties),
        new PositionalEmailTemplateRenderer(),
        dispatcher);
    return new VerificationEmailDispatchService(
        emailDispatchService,
        meterRegistry,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static VerificationEmailDispatchResultVO executeCommitted(
      VerificationEmailDispatchService service) {
    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(request());
    commitTransaction();
    return stage.toCompletableFuture().join();
  }

  private static VerificationEmailDispatchRequestVO request() {
    return new VerificationEmailDispatchRequestVO(
        "person@example.test",
        URI.create("https://rinos.test/login?step=activation&proof=secret-token"),
        "secret-token",
        NOW.plusSeconds(3600),
        Locale.of("pt", "BR"),
        UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab"));
  }

  private static void assertFailureMetrics(
      SimpleMeterRegistry meterRegistry,
      String result) {
    assertThat(meterRegistry.counter(
        VerificationEmailDispatchService.ATTEMPT_METRIC_NAME,
        "result",
        result).count()).isEqualTo(1);
    assertThat(meterRegistry.timer(
        VerificationEmailDispatchService.DURATION_METRIC_NAME,
        "result",
        result).count()).isEqualTo(1);
  }

  private static void beginTransaction() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  private static void commitTransaction() {
    TransactionSynchronizationUtils.triggerAfterCommit();
    TransactionSynchronizationUtils.triggerAfterCompletion(
        TransactionSynchronization.STATUS_COMMITTED);
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  private static void rollbackTransaction() {
    TransactionSynchronizationUtils.triggerAfterCompletion(
        TransactionSynchronization.STATUS_ROLLED_BACK);
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }
}
