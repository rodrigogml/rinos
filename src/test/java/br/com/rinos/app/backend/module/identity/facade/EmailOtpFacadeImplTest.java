package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.rinos.app.api.dto.EmailOtpEmissionRequestDTO;
import br.com.rinos.app.api.dto.EmailOtpVerificationRequestDTO;
import br.com.rinos.app.api.enums.EmailOtpEmissionStatusEnum;
import br.com.rinos.app.api.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.service.EmailOtpDispatchService;
import br.com.rinos.app.backend.module.identity.service.EmailOtpService;
import br.com.rinos.app.backend.module.identity.vo.EmailOtpEmissionDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedEmailOtpVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;

@DisplayName("Fachada do OTP por e-mail")
@ExtendWith(MockitoExtension.class)
class EmailOtpFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");
  private static final UUID CORRELATION =
      UUID.fromString("e1db5d63-aeb5-4b5a-a450-1a751988cf2b");

  @Mock
  private EmailOtpService service;
  @Mock
  private EmailOtpDispatchService dispatcher;
  private EmailOtpFacadeImpl facade;

  @BeforeEach
  void setUp() {
    facade = new EmailOtpFacadeImpl(service, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void begin_shouldPublishEmissionOnlyAfterSmtpAcceptance() {
    when(service.issue("flow", false, NOW)).thenReturn(emitted());
    when(dispatcher.scheduleAfterCommit(any())).thenReturn(CompletableFuture.completedFuture(
        dispatch(VerificationEmailDispatchStatusEnum.ACCEPTED)));

    var result = facade.begin(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(EmailOtpEmissionStatusEnum.EMITTED);
    assertThat(result.challengeReference()).isEqualTo("flow");
    assertThat(result.maskedDestination()).isEqualTo("p***@example.test");
    verify(service, never()).invalidateFailedDelivery(any(), any(), any());
  }

  @Test
  void resend_shouldInvalidateExactProofAndReturnUnavailableWhenSmtpFails() {
    when(service.issue("flow", true, NOW)).thenReturn(emitted());
    when(dispatcher.scheduleAfterCommit(any())).thenReturn(CompletableFuture.completedFuture(
        dispatch(VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE)));

    var result = facade.resend(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(EmailOtpEmissionStatusEnum.UNAVAILABLE);
    verify(service).invalidateFailedDelivery(eq("flow"), aryEq(new byte[32]), eq(NOW));
  }

  @Test
  void begin_shouldPreserveRetryInstantWithoutSchedulingSmtp() {
    when(service.issue("flow", false, NOW)).thenReturn(
        EmailOtpEmissionDecisionVO.rateLimited(NOW.plusSeconds(60)));

    var result = facade.begin(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(EmailOtpEmissionStatusEnum.RATE_LIMITED);
    assertThat(result.retryAfter()).isEqualTo(NOW.plusSeconds(60));
    verify(dispatcher, never()).scheduleAfterCommit(any());
  }

  @Test
  void verify_shouldMapConsumptionAndFailClosed() {
    EmailOtpVerificationRequestDTO request = new EmailOtpVerificationRequestDTO(
        "flow", "123456", NOW);
    when(service.verify("flow", "123456", NOW)).thenReturn(
        br.com.rinos.app.backend.module.identity.enums.EmailOtpVerificationStatusEnum.USED);

    assertThat(facade.verify(request)).isEqualTo(EmailOtpVerificationStatusEnum.USED);

    when(service.verify("flow", "123456", NOW)).thenThrow(new IllegalStateException("db"));
    assertThat(facade.verify(request)).isEqualTo(EmailOtpVerificationStatusEnum.UNAVAILABLE);
    assertThat(request.toString()).contains("proof=REDACTED").doesNotContain("123456");
  }

  private static EmailOtpEmissionRequestDTO request() {
    return new EmailOtpEmissionRequestDTO("flow", Locale.of("pt", "BR"), NOW);
  }

  private static EmailOtpEmissionDecisionVO emitted() {
    return EmailOtpEmissionDecisionVO.emitted(new IssuedEmailOtpVO(
        "flow",
        "person@example.test",
        "p***@example.test",
        "123456",
        new byte[32],
        NOW.plusSeconds(300),
        NOW.plusSeconds(60),
        CORRELATION));
  }

  private static VerificationEmailDispatchResultVO dispatch(
      VerificationEmailDispatchStatusEnum status) {
    return new VerificationEmailDispatchResultVO(status, CORRELATION, Duration.ZERO);
  }
}
