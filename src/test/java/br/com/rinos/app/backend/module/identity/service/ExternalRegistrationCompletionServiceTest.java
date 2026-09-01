package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationPurposeEnum;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;

@DisplayName("Conclusão transacional do cadastro Google")
class ExternalRegistrationCompletionServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("95f6724a-67bf-49fe-90f4-873c96b446ab");

  private VerificationService verificationService;
  private LegalConsentService legalConsentService;
  private LocalCredentialService credentialService;
  private ExternalIdentityService externalIdentityService;
  private IdentityAuditService auditService;
  private ExternalRegistrationCompletionService service;

  @BeforeEach
  void setUp() {
    verificationService = mock(VerificationService.class);
    legalConsentService = mock(LegalConsentService.class);
    credentialService = mock(LocalCredentialService.class);
    externalIdentityService = mock(ExternalIdentityService.class);
    auditService = mock(IdentityAuditService.class);
    service = new ExternalRegistrationCompletionService(
        verificationService,
        legalConsentService,
        credentialService,
        externalIdentityService,
        lifecycleWithPersonalContract(),
        new RegistrationLifecycleService(),
        auditService);
  }

  private static UserLifecycleService lifecycleWithPersonalContract() {
    PersonalContractBootstrapPort contracts = request -> new ContractBootstrapResult(
        ContractBootstrapStatus.ALREADY_COMPLETED,
        ContractScope.PERSONAL,
        UUID.randomUUID(),
        null);
    return new UserLifecycleService(mock(AuthSessionService.class), contracts);
  }

  @Test
  void complete_shouldActivateExternalIdentityAndRemoveLocalCredential() {
    RegistrationEntity registration = pendingRegistration();
    ExternalIdentityEntity identity = pendingExternalIdentity(registration.getUser());
    prepareVerified(registration, identity);
    when(legalConsentService.validateCurrentAcceptances(List.of(101L, 102L), NOW))
        .thenReturn(Map.of(
            101L,
            LegalConsentDecisionEnum.ACCEPTED,
            102L,
            LegalConsentDecisionEnum.ACCEPTED));
    doAnswer(invocation -> {
      ExternalIdentityEntity candidate = invocation.getArgument(0);
      Instant activatedAt = invocation.getArgument(1);
      candidate.setStatus(ExternalIdentityStatusEnum.ACTIVE);
      candidate.setActivatedAt(activatedAt);
      return null;
    }).when(externalIdentityService).activate(identity, NOW);

    ExternalRegistrationCompletionResultVO result = service.complete(
        "opaque-reference",
        List.of(101L, 102L),
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.AUTHENTICATED);
    assertThat(result.principal().userId()).isEqualTo(10L);
    assertThat(result.principal().email()).isEqualTo("person@example.com");
    assertThat(registration.getUser().getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
    assertThat(registration.getStatus()).isEqualTo(RegistrationStatusEnum.ACTIVE);
    assertThat(identity.getStatus()).isEqualTo(ExternalIdentityStatusEnum.ACTIVE);
    verify(legalConsentService).recordCurrentDecisions(
        registration.getUser(),
        registration,
        Map.of(
            101L,
            LegalConsentDecisionEnum.ACCEPTED,
            102L,
            LegalConsentDecisionEnum.ACCEPTED),
        NOW);
    verify(credentialService).invalidateAndRemoveForGoogle(10L, NOW);
    verify(verificationService).invalidateAllOpen(20L, NOW);
  }

  @Test
  void complete_shouldRejectExpiredOrReplayedReferenceWithoutSideEffects() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        "expired",
        NOW)).thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.EXPIRED,
            registration,
            NOW));
    when(verificationService.inspect(
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        "used",
        NOW)).thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.ALREADY_USED,
            registration,
            NOW.plusSeconds(60)));

    ExternalRegistrationCompletionResultVO expired = service.complete(
        "expired",
        List.of(),
        CORRELATION_ID,
        NOW);
    ExternalRegistrationCompletionResultVO replay = service.complete(
        "used",
        List.of(),
        CORRELATION_ID,
        NOW);

    assertThat(expired.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.EXPIRED_REFERENCE);
    assertThat(replay.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.INVALID_REFERENCE);
    verify(credentialService, never()).invalidateAndRemoveForGoogle(any(), any());
    verify(externalIdentityService, never()).activate(any(), any());
  }

  @Test
  void complete_shouldRollbackBeforeCredentialRemoval_whenLegalVersionChanged() {
    RegistrationEntity registration = pendingRegistration();
    ExternalIdentityEntity identity = pendingExternalIdentity(registration.getUser());
    prepareVerified(registration, identity);
    when(legalConsentService.validateCurrentAcceptances(List.of(101L), NOW))
        .thenThrow(new IllegalArgumentException("document is no longer current"));

    assertThatThrownBy(() -> service.complete(
        "opaque-reference",
        List.of(101L),
        CORRELATION_ID,
        NOW)).isInstanceOf(IllegalArgumentException.class);

    verify(verificationService, never()).consume(any(), any(), any(), any());
    verify(credentialService, never()).invalidateAndRemoveForGoogle(any(), any());
    verify(externalIdentityService, never()).activate(any(), any());
  }

  @Test
  void complete_shouldLoseRaceAfterLocalActivationChangedRegistration() {
    RegistrationEntity registration = pendingRegistration();
    registration.setStatus(RegistrationStatusEnum.ACTIVE);
    registration.getUser().setStatus(UserStatusEnum.ACTIVE);
    when(verificationService.inspect(
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        "opaque-reference",
        NOW)).thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.VERIFIED,
            registration,
            NOW.plusSeconds(60)));

    ExternalRegistrationCompletionResultVO result = service.complete(
        "opaque-reference",
        List.of(),
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.CONFLICT);
    verify(externalIdentityService, never()).findSinglePendingForUpdate(any());
    verify(credentialService, never()).invalidateAndRemoveForGoogle(any(), any());
  }

  @Test
  void complete_shouldPreserveCredential_whenExternalCandidateDisappeared() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        "opaque-reference",
        NOW)).thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.VERIFIED,
            registration,
            NOW.plusSeconds(60)));
    when(externalIdentityService.findSinglePendingForUpdate(10L))
        .thenReturn(Optional.empty());

    ExternalRegistrationCompletionResultVO result = service.complete(
        "opaque-reference",
        List.of(),
        CORRELATION_ID,
        NOW);

    assertThat(result.status())
        .isEqualTo(ExternalRegistrationCompletionStatusEnum.CONFLICT);
    verify(legalConsentService, never())
        .validateCurrentAcceptances(any(), any());
    verify(verificationService, never()).consume(any(), any(), any(), any());
    verify(credentialService, never()).invalidateAndRemoveForGoogle(any(), any());
  }

  private void prepareVerified(
      RegistrationEntity registration,
      ExternalIdentityEntity identity) {
    when(verificationService.inspect(
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        "opaque-reference",
        NOW)).thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.VERIFIED,
            registration,
            NOW.plus(Duration.ofHours(1))));
    when(externalIdentityService.findSinglePendingForUpdate(10L))
        .thenReturn(Optional.of(identity));
    when(verificationService.consume(
        20L,
        VerificationPurposeEnum.EXTERNAL_REGISTRATION,
        "opaque-reference",
        NOW)).thenReturn(VerificationConsumptionStatusEnum.VERIFIED);
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
        NOW.plus(Duration.ofDays(1)));
    ReflectionTestUtils.setField(registration, "id", 20L);
    return registration;
  }

  private static ExternalIdentityEntity pendingExternalIdentity(UserEntity user) {
    return new ExternalIdentityEntity(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "stable-subject",
        NOW);
  }
}
