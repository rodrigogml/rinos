package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import br.com.rinos.app.backend.module.identity.vo.EmailOtpDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.eng.rodrigogml.rfw.exception.RFWExceptionCodeDefinitions;
import br.eng.rodrigogml.rfw.exception.RFWIntegrationException;
import br.eng.rodrigogml.rfw.mail.ClasspathEmailTemplateResolver;
import br.eng.rodrigogml.rfw.mail.EmailDispatchService;
import br.eng.rodrigogml.rfw.mail.EmailDispatcher;
import br.eng.rodrigogml.rfw.mail.EmailMessage;
import br.eng.rodrigogml.rfw.mail.PositionalEmailTemplateRenderer;
import br.eng.rodrigogml.rfw.mail.config.EmailTemplatePropertiesConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Despacho pós-commit do OTP por e-mail")
@ExtendWith(OutputCaptureExtension.class)
class EmailOtpDispatchServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");

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
    EmailOtpDispatchService service = service(dispatched::set);

    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(request());

    assertThat(stage.toCompletableFuture()).isNotDone();
    assertThat(dispatched).hasValue(null);

    commitTransaction();

    assertThat(stage.toCompletableFuture().join().status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.ACCEPTED);
    assertThat(dispatched.get().toAddresses()).containsExactly("person@example.test");
    assertThat(dispatched.get().subject()).contains("código de acesso");
    assertThat(dispatched.get().body()).contains("<code>123456</code>", "expira");
  }

  @Test
  void scheduleAfterCommit_shouldNotDispatchAfterRollback() {
    AtomicInteger attempts = new AtomicInteger();
    EmailOtpDispatchService service = service(ignored -> attempts.incrementAndGet());

    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(request());
    rollbackTransaction();

    assertThat(stage.toCompletableFuture().join().status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.TRANSACTION_ROLLED_BACK);
    assertThat(attempts).hasValue(0);
  }

  @Test
  void scheduleAfterCommit_shouldExposeTransportFailureWithoutLeakingSecrets(
      CapturedOutput output) {
    EmailOtpDispatchService service = service(ignored -> {
      throw new RFWIntegrationException(
          RFWExceptionCodeDefinitions.SMTP_DISPATCH_FAILED,
          "person@example.test 123456");
    });

    beginTransaction();
    CompletionStage<VerificationEmailDispatchResultVO> stage =
        service.scheduleAfterCommit(request());
    commitTransaction();

    assertThat(stage.toCompletableFuture().join().status())
        .isEqualTo(VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE);
    assertThat(output.getOut()).doesNotContain("person@example.test", "123456");
  }

  @Test
  void scheduleAfterCommit_shouldRejectCallOutsideTransaction() {
    assertThatThrownBy(() -> service(ignored -> { }).scheduleAfterCommit(request()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active transaction");
  }

  @Test
  void request_shouldRejectMarkupAndRedactMaterial() {
    assertThatThrownBy(() -> new EmailOtpDispatchRequestVO(
        "person@example.test", "<script>", NOW.plusSeconds(300), Locale.ROOT,
        UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    assertThat(request().toString())
        .contains("recipient=REDACTED", "code=REDACTED")
        .doesNotContain("person@example.test", "123456");
  }

  private static EmailOtpDispatchService service(EmailDispatcher dispatcher) {
    EmailTemplatePropertiesConfig properties = new EmailTemplatePropertiesConfig();
    EmailDispatchService dispatch = new EmailDispatchService(
        new ClasspathEmailTemplateResolver(new DefaultResourceLoader(), properties),
        new PositionalEmailTemplateRenderer(),
        dispatcher);
    return new EmailOtpDispatchService(
        dispatch,
        new SimpleMeterRegistry(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static EmailOtpDispatchRequestVO request() {
    return new EmailOtpDispatchRequestVO(
        "person@example.test",
        "123456",
        NOW.plusSeconds(300),
        Locale.of("pt", "BR"),
        UUID.fromString("e1db5d63-aeb5-4b5a-a450-1a751988cf2b"));
  }

  private static void beginTransaction() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
  }

  private static void commitTransaction() {
    TransactionSynchronizationUtils.triggerBeforeCommit(false);
    TransactionSynchronizationUtils.triggerBeforeCompletion();
    TransactionSynchronizationUtils.triggerAfterCommit();
    TransactionSynchronizationUtils.triggerAfterCompletion(
        TransactionSynchronization.STATUS_COMMITTED);
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  private static void rollbackTransaction() {
    TransactionSynchronizationUtils.triggerBeforeCompletion();
    TransactionSynchronizationUtils.triggerAfterCompletion(
        TransactionSynchronization.STATUS_ROLLED_BACK);
    TransactionSynchronizationManager.clearSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }
}
