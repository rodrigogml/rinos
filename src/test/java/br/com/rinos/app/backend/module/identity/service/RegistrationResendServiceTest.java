package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationResendTransactionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("Reemissão transacional de comprovação")
class RegistrationResendServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab");
  private static final Locale LOCALE = Locale.of("pt", "BR");

  @Mock
  private RegistrationRepository registrationRepository;

  @Mock
  private IdentityEventRepository eventRepository;

  @Mock
  private VerificationService verificationService;

  @Mock
  private IdentityAuditService auditService;

  @Mock
  private PublicApplicationUriService uriService;

  @Mock
  private VerificationEmailDispatchService dispatchService;

  @Mock
  private RegistrationEntity registration;

  @Mock
  private UserEntity user;

  private RegistrationResendService service;

  @BeforeEach
  void setUp() {
    service = new RegistrationResendService(
        registrationRepository,
        eventRepository,
        verificationService,
        auditService,
        uriService,
        dispatchService,
        new RegistrationPropertiesConfig(
            Duration.ofDays(15),
            3,
            Duration.ofMinutes(15)));
  }

  @Test
  void resend_shouldCreateDistinctProofForEveryExplicitRequest_whenBelowLimit() {
    prepareEligibleRegistration();
    when(registration.getUser()).thenReturn(user);
    when(user.getEmail()).thenReturn("person@example.test");
    when(eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            eq(31L),
            eq(IdentityEventTypeEnum.VERIFICATION_REISSUED),
            any())).thenReturn(List.of());
    when(verificationService.issue(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        NOW)).thenReturn(
            new IssuedVerificationVO(41L, "first-token", NOW.plus(Duration.ofHours(24))),
            new IssuedVerificationVO(42L, "second-token", NOW.plus(Duration.ofHours(24))));
    when(uriService.activationUri("first-token"))
        .thenReturn(URI.create(
            "https://app.rinos.com.br/login?step=activation&proof=first-token"));
    when(uriService.activationUri("second-token"))
        .thenReturn(URI.create(
            "https://app.rinos.com.br/login?step=activation&proof=second-token"));
    when(dispatchService.scheduleAfterCommit(any())).thenReturn(
        acceptedDispatch(),
        acceptedDispatch());

    RegistrationResendTransactionVO first =
        service.resend(31L, LOCALE, CORRELATION_ID, NOW);
    RegistrationResendTransactionVO second =
        service.resend(31L, LOCALE, CORRELATION_ID, NOW);

    assertThat(first.dispatch()).isNotNull();
    assertThat(second.dispatch()).isNotNull();
    ArgumentCaptor<VerificationEmailDispatchRequestVO> requests =
        ArgumentCaptor.forClass(VerificationEmailDispatchRequestVO.class);
    verify(dispatchService, org.mockito.Mockito.times(2))
        .scheduleAfterCommit(requests.capture());
    assertThat(requests.getAllValues())
        .extracting(request -> request.confirmationUrl().toASCIIString())
        .containsExactly(
            "https://app.rinos.com.br/login?step=activation&proof=first-token",
            "https://app.rinos.com.br/login?step=activation&proof=second-token");
    assertThat(requests.getAllValues())
        .extracting(VerificationEmailDispatchRequestVO::manualCode)
        .containsExactly("first-token", "second-token");
    verify(verificationService, org.mockito.Mockito.times(2)).issue(
        registration,
        VerificationPurposeEnum.REGISTRATION_EMAIL,
        NOW);
  }

  @Test
  void resend_shouldBlockFourthRequest_untilOldestEventLeavesWindow() {
    prepareEligibleRegistration();
    IdentityEventEntity first = org.mockito.Mockito.mock(IdentityEventEntity.class);
    IdentityEventEntity second = org.mockito.Mockito.mock(IdentityEventEntity.class);
    IdentityEventEntity third = org.mockito.Mockito.mock(IdentityEventEntity.class);
    when(first.getOccurredAt()).thenReturn(NOW.minus(Duration.ofMinutes(10)));
    when(eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            31L,
            IdentityEventTypeEnum.VERIFICATION_REISSUED,
            NOW.minus(Duration.ofMinutes(15))))
        .thenReturn(List.of(first, second, third));

    RegistrationResendTransactionVO result =
        service.resend(31L, LOCALE, CORRELATION_ID, NOW);

    assertThat(result.blocked()).isTrue();
    assertThat(result.blockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    verify(verificationService, never()).issue(any(), any(), any());
    verify(dispatchService, never()).scheduleAfterCommit(any());
  }

  @Test
  void resend_shouldReturnNeutralOutcome_withoutIssuingProof_whenRegistrationExpired() {
    when(registrationRepository.findByIdForUpdate(31L))
        .thenReturn(Optional.of(registration));
    when(registration.getMethod()).thenReturn(RegistrationMethodEnum.LOCAL);
    when(registration.getStatus()).thenReturn(RegistrationStatusEnum.PENDING_VERIFICATION);
    when(registration.getExpiresAt()).thenReturn(NOW);

    RegistrationResendTransactionVO result =
        service.resend(31L, LOCALE, CORRELATION_ID, NOW);

    assertThat(result.eligible()).isFalse();
    verify(eventRepository, never())
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            any(), any(), any());
    verify(verificationService, never()).issue(any(), any(), any());
  }

  private void prepareEligibleRegistration() {
    when(registrationRepository.findByIdForUpdate(31L))
        .thenReturn(Optional.of(registration));
    when(registration.getMethod()).thenReturn(RegistrationMethodEnum.LOCAL);
    when(registration.getStatus()).thenReturn(RegistrationStatusEnum.PENDING_VERIFICATION);
    when(registration.getExpiresAt()).thenReturn(NOW.plus(Duration.ofDays(2)));
  }

  private CompletableFuture<VerificationEmailDispatchResultVO> acceptedDispatch() {
    return CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
        VerificationEmailDispatchStatusEnum.ACCEPTED,
        CORRELATION_ID,
        Duration.ofMillis(10)));
  }
}
