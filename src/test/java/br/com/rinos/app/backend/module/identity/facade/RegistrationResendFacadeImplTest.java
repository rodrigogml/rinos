package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.rinos.app.api.dto.RegistrationResendRequestDTO;
import br.com.rinos.app.api.enums.RegistrationResendStatusEnum;
import br.com.rinos.app.api.vo.RegistrationResendResultVO;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.service.RegistrationResendService;
import br.com.rinos.app.backend.module.identity.vo.RegistrationResendTransactionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fachada de reenvio da comprovação")
class RegistrationResendFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab");

  @Mock
  private IdentityService identityService;

  @Mock
  private RegistrationResendService resendService;

  @Mock
  private RegistrationEntity registration;

  @Mock
  private RegistrationObservabilityService observabilityService;

  private RegistrationResendFacadeImpl facade;

  @BeforeEach
  void setUp() {
    facade = new RegistrationResendFacadeImpl(
        identityService,
        resendService,
        observabilityService,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void resend_shouldReturnNeutralAcceptance_whenPendingRegistrationDoesNotExist() {
    when(identityService.findPendingRegistration("unknown@example.test"))
        .thenReturn(Optional.empty());

    RegistrationResendResultVO result =
        facade.resend(request("unknown@example.test")).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(RegistrationResendStatusEnum.REQUEST_ACCEPTED);
    verify(resendService, never()).resend(any(), any(), any(), any());
  }

  @Test
  void resend_shouldExposeRemainingWindow_whenLimitWasReached() {
    when(identityService.findPendingRegistration("person@example.test"))
        .thenReturn(Optional.of(registration));
    when(registration.getId()).thenReturn(31L);
    when(resendService.resend(31L, Locale.of("pt", "BR"), CORRELATION_ID, NOW))
        .thenReturn(RegistrationResendTransactionVO.blocked(
            NOW.plus(Duration.ofMinutes(4))));

    RegistrationResendResultVO result =
        facade.resend(request("person@example.test")).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(RegistrationResendStatusEnum.RATE_LIMITED);
    assertThat(result.retryAfter()).isEqualTo(Duration.ofMinutes(4));
    verify(observabilityService).recordOperation(
        RegistrationOperationEnum.RESEND,
        RegistrationResendStatusEnum.RATE_LIMITED.name(),
        CORRELATION_ID,
        NOW,
        NOW);
  }

  @Test
  void resend_shouldAllowExplicitRecovery_afterPreviousSmtpFailure() {
    when(identityService.findPendingRegistration("person@example.test"))
        .thenReturn(Optional.of(registration));
    when(registration.getId()).thenReturn(31L);
    when(resendService.resend(31L, Locale.of("pt", "BR"), CORRELATION_ID, NOW))
        .thenReturn(
            RegistrationResendTransactionVO.scheduled(
                NOW.plusSeconds(3_600),
                CompletableFuture.completedFuture(
                    dispatch(VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE))),
            RegistrationResendTransactionVO.scheduled(
                NOW.plusSeconds(7_200),
                CompletableFuture.completedFuture(
                    dispatch(VerificationEmailDispatchStatusEnum.ACCEPTED))));

    RegistrationResendResultVO first =
        facade.resend(request("person@example.test")).toCompletableFuture().join();
    RegistrationResendResultVO second =
        facade.resend(request("person@example.test")).toCompletableFuture().join();

    assertThat(first.status())
        .isEqualTo(RegistrationResendStatusEnum.EMAIL_DISPATCH_FAILED);
    assertThat(second.status())
        .isEqualTo(RegistrationResendStatusEnum.REQUEST_ACCEPTED);
    assertThat(second.expiresAt()).isEqualTo(NOW.plusSeconds(7_200));
    verify(resendService, org.mockito.Mockito.times(2))
        .resend(31L, Locale.of("pt", "BR"), CORRELATION_ID, NOW);
  }

  private RegistrationResendRequestDTO request(String identifier) {
    return new RegistrationResendRequestDTO(
        identifier,
        Locale.of("pt", "BR"),
        CORRELATION_ID);
  }

  private VerificationEmailDispatchResultVO dispatch(
      VerificationEmailDispatchStatusEnum status) {
    return new VerificationEmailDispatchResultVO(
        status,
        CORRELATION_ID,
        Duration.ofMillis(10));
  }
}
