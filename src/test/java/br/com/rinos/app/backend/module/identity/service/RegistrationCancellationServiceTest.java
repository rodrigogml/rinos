package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;
import br.com.rinos.app.backend.module.identity.entity.IdentityEventEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.IdentityEventRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.IssuedVerificationVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCancellationIssueVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchRequestVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;
import br.com.rinos.app.config.RegistrationPropertiesConfig;

@DisplayName("Cancelamento transacional de cadastro pendente")
class RegistrationCancellationServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("75507575-31b7-41b9-b103-55cdf417a622");
  private static final String PROOF = "cancellation-proof";

  private RegistrationRepository registrationRepository;
  private IdentityEventRepository eventRepository;
  private UserRepository userRepository;
  private VerificationService verificationService;
  private IdentityAuditService auditService;
  private PublicApplicationUriService uriService;
  private VerificationEmailDispatchService dispatchService;
  private RegistrationCancellationService service;

  @BeforeEach
  void setUp() {
    registrationRepository = mock(RegistrationRepository.class);
    eventRepository = mock(IdentityEventRepository.class);
    userRepository = mock(UserRepository.class);
    verificationService = mock(VerificationService.class);
    auditService = mock(IdentityAuditService.class);
    uriService = mock(PublicApplicationUriService.class);
    dispatchService = mock(VerificationEmailDispatchService.class);
    service = new RegistrationCancellationService(
        registrationRepository,
        eventRepository,
        userRepository,
        verificationService,
        new EmailNormalizationService(),
        new RegistrationLifecycleService(),
        new UserLifecycleService(),
        auditService,
        uriService,
        dispatchService,
        new RegistrationPropertiesConfig(
            Duration.ofDays(15),
            3,
            Duration.ofMinutes(15),
            3,
            Duration.ofMinutes(15)));
  }

  @Test
  void issue_shouldCreateCancellationProofWithoutCancellingRegistration() {
    RegistrationEntity registration = pendingRegistration();
    when(registrationRepository.findByIdForUpdate(20L))
        .thenReturn(java.util.Optional.of(registration));
    when(eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            20L,
            IdentityEventTypeEnum.REGISTRATION_CANCELLATION_REQUESTED,
            NOW.minus(Duration.ofMinutes(15))))
        .thenReturn(List.of());
    when(verificationService.issue(any(), any(), any()))
        .thenReturn(new IssuedVerificationVO(
            30L,
            PROOF,
            NOW.plusSeconds(3600)));
    when(uriService.registrationCancellationUri(PROOF))
        .thenReturn(URI.create(
            "https://app.rinos.com.br/cancel-registration?token=cancellation-proof"));
    CompletableFuture<VerificationEmailDispatchResultVO> dispatch =
        CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
            VerificationEmailDispatchStatusEnum.ACCEPTED,
            CORRELATION_ID,
            java.time.Duration.ZERO));
    when(dispatchService.scheduleAfterCommit(any())).thenReturn(dispatch);

    RegistrationCancellationIssueVO result = service.issue(
        20L,
        Locale.of("pt", "BR"),
        CORRELATION_ID,
        NOW);

    assertThat(result.issued()).isTrue();
    assertThat(result.dispatch()).isSameAs(dispatch);
    assertThat(registration.getStatus())
        .isEqualTo(RegistrationStatusEnum.PENDING_VERIFICATION);
    ArgumentCaptor<VerificationEmailDispatchRequestVO> request =
        ArgumentCaptor.forClass(VerificationEmailDispatchRequestVO.class);
    verify(dispatchService).scheduleAfterCommit(request.capture());
    assertThat(request.getValue().template().name())
        .isEqualTo("REGISTRATION_CANCELLATION");
  }

  @Test
  void issue_shouldBlockFourthProofWithoutDispatch_whenWindowIsFull() {
    RegistrationEntity registration = pendingRegistration();
    when(registrationRepository.findByIdForUpdate(20L))
        .thenReturn(java.util.Optional.of(registration));
    IdentityEventEntity first = mock(IdentityEventEntity.class);
    when(first.getOccurredAt()).thenReturn(NOW.minus(Duration.ofMinutes(10)));
    when(eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            20L,
            IdentityEventTypeEnum.REGISTRATION_CANCELLATION_REQUESTED,
            NOW.minus(Duration.ofMinutes(15))))
        .thenReturn(List.of(first, mock(IdentityEventEntity.class), mock(IdentityEventEntity.class)));

    RegistrationCancellationIssueVO result = service.issue(
        20L,
        Locale.of("pt", "BR"),
        CORRELATION_ID,
        NOW);

    assertThat(result.rateLimited()).isTrue();
    assertThat(result.blockedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    verify(verificationService, never()).issue(any(), any(), any());
    verify(dispatchService, never()).scheduleAfterCommit(any());
  }

  @Test
  void issue_shouldAllowNewProof_whenOldestEventHasLeftWindow() {
    RegistrationEntity registration = pendingRegistration();
    when(registrationRepository.findByIdForUpdate(20L))
        .thenReturn(java.util.Optional.of(registration));
    when(eventRepository
        .findByRegistrationIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtAsc(
            20L,
            IdentityEventTypeEnum.REGISTRATION_CANCELLATION_REQUESTED,
            NOW.minus(Duration.ofMinutes(15))))
        .thenReturn(List.of(mock(IdentityEventEntity.class), mock(IdentityEventEntity.class)));
    when(verificationService.issue(any(), any(), any()))
        .thenReturn(new IssuedVerificationVO(30L, PROOF, NOW.plusSeconds(3600)));
    when(uriService.registrationCancellationUri(PROOF))
        .thenReturn(URI.create(
            "https://app.rinos.com.br/cancel-registration?token=cancellation-proof"));
    when(dispatchService.scheduleAfterCommit(any())).thenReturn(
        CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
            VerificationEmailDispatchStatusEnum.ACCEPTED,
            CORRELATION_ID,
            Duration.ZERO)));

    RegistrationCancellationIssueVO result = service.issue(
        20L,
        Locale.of("pt", "BR"),
        CORRELATION_ID,
        NOW);

    assertThat(result.issued()).isTrue();
    verify(verificationService).issue(any(), any(), any());
    verify(dispatchService).scheduleAfterCommit(any());
  }

  @Test
  void confirm_shouldDeleteRootAndCreatePiiFreeTombstone_whenProofMatchesEmail() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.VERIFIED,
            registration,
            NOW.plusSeconds(3600)));
    when(verificationService.consume(any(), any(), any(), any()))
        .thenReturn(VerificationConsumptionStatusEnum.VERIFIED);

    RegistrationCancellationConfirmationStatusEnum result = service.confirm(
        " PERSON@example.com ",
        PROOF,
        CORRELATION_ID,
        NOW);

    assertThat(result)
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.CANCELLED);
    assertThat(registration.getStatus()).isEqualTo(RegistrationStatusEnum.CANCELLED);
    assertThat(registration.getUser().getStatus()).isEqualTo(UserStatusEnum.CANCELLED);
    verify(verificationService).invalidateAllOpen(20L, NOW);
    verify(userRepository).delete(registration.getUser());
    verify(userRepository).flush();
    verify(auditService).recordCancellationTombstone(
        CORRELATION_ID,
        br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum.SELF_SERVICE,
        "EMAIL_CONTROL_CONFIRMED",
        NOW);
  }

  @Test
  void confirm_shouldNotConsumeProof_whenIdentifierDoesNotMatch() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.VERIFIED,
            registration,
            NOW.plusSeconds(3600)));

    RegistrationCancellationConfirmationStatusEnum result = service.confirm(
        "other@example.com",
        PROOF,
        CORRELATION_ID,
        NOW);

    assertThat(result)
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF);
    verify(verificationService, never()).consume(any(), any(), any(), any());
    verify(userRepository, never()).delete(any());
  }

  @Test
  void confirm_shouldLeaveEverythingUntouched_whenProofIsInvalid() {
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.REJECTED,
            null,
            null));

    RegistrationCancellationConfirmationStatusEnum result = service.confirm(
        "person@example.com",
        PROOF,
        CORRELATION_ID,
        NOW);

    assertThat(result)
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF);
    verify(userRepository, never()).delete(any());
    verify(auditService, never()).recordCancellationTombstone(any(), any(), any(), any());
  }

  @Test
  void confirm_shouldTreatUsedProofAsInvalidWithoutRepeatingCancellation() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.ALREADY_USED,
            registration,
            NOW.plusSeconds(3600)));

    RegistrationCancellationConfirmationStatusEnum result = service.confirm(
        "person@example.com",
        PROOF,
        CORRELATION_ID,
        NOW);

    assertThat(result)
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF);
    verify(verificationService, never()).consume(any(), any(), any(), any());
    verify(userRepository, never()).delete(any());
    verify(auditService, never()).recordCancellationTombstone(any(), any(), any(), any());
  }

  @Test
  void confirm_shouldReportExpiredProofWithoutCancellingRegistration() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.EXPIRED,
            registration,
            NOW));

    RegistrationCancellationConfirmationStatusEnum result = service.confirm(
        "person@example.com",
        PROOF,
        CORRELATION_ID,
        NOW);

    assertThat(result)
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.EXPIRED_PROOF);
    verify(verificationService, never()).consume(any(), any(), any(), any());
    verify(userRepository, never()).delete(any());
    verify(auditService, never()).recordCancellationTombstone(any(), any(), any(), any());
  }

  @Test
  void confirm_shouldTreatClosedRegistrationAsInvalidWithoutConsumingProof() {
    RegistrationEntity registration = pendingRegistration();
    registration.setStatus(RegistrationStatusEnum.ACTIVE);
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.VERIFIED,
            registration,
            NOW.plusSeconds(3600)));

    RegistrationCancellationConfirmationStatusEnum result = service.confirm(
        "person@example.com",
        PROOF,
        CORRELATION_ID,
        NOW);

    assertThat(result)
        .isEqualTo(RegistrationCancellationConfirmationStatusEnum.INVALID_PROOF);
    verify(verificationService, never()).consume(any(), any(), any(), any());
    verify(userRepository, never()).delete(any());
    verify(auditService, never()).recordCancellationTombstone(any(), any(), any(), any());
  }

  private static RegistrationEntity pendingRegistration() {
    UserEntity user = new UserEntity(
        "person@example.com",
        "person@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    ReflectionTestUtils.setField(user, "id", 10L);
    RegistrationEntity registration = new RegistrationEntity(
        user,
        RegistrationMethodEnum.LOCAL,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        NOW.plusSeconds(86_400));
    ReflectionTestUtils.setField(registration, "id", 20L);
    return registration;
  }
}
