package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;

import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordPolicyViolationEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationEmailDispatchStatusEnum;
import br.com.rinos.app.backend.module.identity.service.IdentityService;
import br.com.rinos.app.backend.module.identity.service.IdentityAuditService;
import br.com.rinos.app.backend.module.identity.service.LegalConsentService;
import br.com.rinos.app.backend.module.identity.service.OriginAddressService;
import br.com.rinos.app.backend.module.identity.service.PasswordPreparationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationCreationService;
import br.com.rinos.app.backend.module.identity.service.RegistrationObservabilityService;
import br.com.rinos.app.backend.module.identity.vo.PasswordPreparationResultVO;
import br.com.rinos.app.backend.module.identity.vo.PasswordValidationResultVO;
import br.com.rinos.app.backend.module.identity.vo.RegistrationCreationTransactionVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO;

@ExtendWith(MockitoExtension.class)
@DisplayName("Fachada de início do cadastro")
class RegistrationStartFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  @Mock
  private IdentityService identityService;

  @Mock
  private PasswordPreparationService passwordPreparationService;

  @Mock
  private LegalConsentService legalConsentService;

  @Mock
  private RegistrationCreationService registrationCreationService;

  @Mock
  private IdentityAuditService auditService;

  @Mock
  private RegistrationObservabilityService observabilityService;

  private RegistrationStartFacadeImpl facade;

  @BeforeEach
  void setUp() {
    facade = new RegistrationStartFacadeImpl(
        identityService,
        passwordPreparationService,
        legalConsentService,
        new OriginAddressService(),
        registrationCreationService,
        auditService,
        observabilityService,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void start_shouldReturnExistingEmail_withoutTouchingPassword_whenUserIsActive() {
    when(identityService.findByEmail("person@example.test")).thenReturn(Optional.of(
        new UserEntity("person@example.test", "person@example.test", UserStatusEnum.ACTIVE)));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(RegistrationStartStatusEnum.EMAIL_ALREADY_EXISTS);
    verify(passwordPreparationService, never()).prepare(any());
    verify(auditService).record(
        any(),
        eq(null),
        eq(request().getCorrelationId()),
        eq(IdentityEventTypeEnum.REGISTRATION_REJECTED),
        eq(null),
        eq(null),
        eq(IdentityTransitionOriginEnum.SELF_SERVICE),
        eq(RegistrationStartStatusEnum.EMAIL_ALREADY_EXISTS.name()),
        eq(NOW));
  }

  @Test
  void start_shouldConvergeWithoutReplacingPassword_whenRegistrationIsPending() {
    when(identityService.findByEmail("person@example.test")).thenReturn(Optional.of(
        new UserEntity(
            "person@example.test",
            "person@example.test",
            UserStatusEnum.PENDING_VERIFICATION)));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(
        RegistrationStartStatusEnum.PENDING_ALREADY_EXISTS);
    verify(passwordPreparationService, never()).prepare(any());
  }

  @Test
  void start_shouldExposePasswordFieldError_beforeAnyWrite() {
    when(identityService.findByEmail("person@example.test")).thenReturn(Optional.empty());
    when(passwordPreparationService.prepare(any())).thenReturn(
        new PasswordPreparationResultVO(
            new PasswordValidationResultVO(
                List.of(PasswordPolicyViolationEnum.MINIMUM_LENGTH_REQUIRED)),
            null));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(RegistrationStartStatusEnum.VALIDATION_REJECTED);
    assertThat(result.fieldErrors().get("password"))
        .isEqualTo("registration.error.password.minimum-length-required");
    verify(registrationCreationService, never()).create(
        any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void start_shouldConfirmOnlyAfterSmtpAcceptance() {
    prepareValidRequest();
    when(registrationCreationService.create(
        any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(RegistrationCreationTransactionVO.scheduled(
            NOW.plusSeconds(3_600),
            CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
                VerificationEmailDispatchStatusEnum.ACCEPTED,
                request().getCorrelationId(),
                java.time.Duration.ofMillis(10)))));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(RegistrationStartStatusEnum.EMAIL_SENT);
    assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(3_600));
    verify(observabilityService).recordOperation(
        RegistrationOperationEnum.START,
        RegistrationStartStatusEnum.EMAIL_SENT.name(),
        request().getCorrelationId(),
        NOW,
        NOW);
  }

  @Test
  void start_shouldPreservePendingOutcome_whenSmtpFails() {
    prepareValidRequest();
    when(registrationCreationService.create(
        any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(RegistrationCreationTransactionVO.scheduled(
            NOW.plusSeconds(3_600),
            CompletableFuture.completedFuture(new VerificationEmailDispatchResultVO(
                VerificationEmailDispatchStatusEnum.TRANSPORT_FAILURE,
                request().getCorrelationId(),
                java.time.Duration.ofSeconds(1)))));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status())
        .isEqualTo(RegistrationStartStatusEnum.EMAIL_DISPATCH_FAILED);
  }

  @Test
  void start_shouldReturnRetryAfter_whenOriginLimitBlocksCreation() {
    prepareValidRequest();
    when(registrationCreationService.create(
        any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(RegistrationCreationTransactionVO.blocked(NOW.plusSeconds(60)));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(RegistrationStartStatusEnum.RATE_LIMITED);
    assertThat(result.retryAfter()).isEqualTo(java.time.Duration.ofSeconds(60));
  }

  @Test
  void start_shouldConvergeToConcurrentWinner_whenUniqueEmailConstraintCollides() {
    UserEntity winner = new UserEntity(
        "person@example.test",
        "person@example.test",
        UserStatusEnum.PENDING_VERIFICATION);
    when(identityService.findByEmail("person@example.test"))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(passwordPreparationService.prepare(any())).thenReturn(
        new PasswordPreparationResultVO(
            new PasswordValidationResultVO(List.of()),
            "{argon2}hash"));
    when(legalConsentService.validatePublishedAcceptances(any(), any()))
        .thenReturn(Map.of(1L, LegalConsentDecisionEnum.ACCEPTED));
    when(registrationCreationService.create(
        any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new DataIntegrityViolationException("concurrent winner"));

    RegistrationStartResultVO result =
        facade.start(request()).toCompletableFuture().join();

    assertThat(result.status())
        .isEqualTo(RegistrationStartStatusEnum.PENDING_ALREADY_EXISTS);
    verify(auditService).record(
        eq(winner),
        eq(null),
        eq(request().getCorrelationId()),
        eq(IdentityEventTypeEnum.REGISTRATION_REJECTED),
        eq(null),
        eq(null),
        eq(IdentityTransitionOriginEnum.SELF_SERVICE),
        eq(RegistrationStartStatusEnum.PENDING_ALREADY_EXISTS.name()),
        eq(NOW));
  }

  private void prepareValidRequest() {
    when(identityService.findByEmail("person@example.test")).thenReturn(Optional.empty());
    when(passwordPreparationService.prepare(any())).thenReturn(
        new PasswordPreparationResultVO(new PasswordValidationResultVO(List.of()), "{argon2}hash"));
    when(legalConsentService.validatePublishedAcceptances(any(), any()))
        .thenReturn(Map.of(1L, LegalConsentDecisionEnum.ACCEPTED));
  }

  private RegistrationStartRequestDTO request() {
    return new RegistrationStartRequestDTO(
        "person@example.test",
        "ValidPassword1!".toCharArray(),
        List.of("1"),
        "203.0.113.10",
        java.util.Locale.of("pt", "BR"),
        UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab"));
  }
}
