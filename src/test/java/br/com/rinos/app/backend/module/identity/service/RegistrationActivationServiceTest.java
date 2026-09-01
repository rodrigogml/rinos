package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.api.enums.RegistrationActivationStatusEnum;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.api.module.plans.enums.ContractBootstrapStatus;
import br.com.rinos.app.api.module.plans.enums.ContractScope;
import br.com.rinos.app.api.module.plans.port.PersonalContractBootstrapPort;
import br.com.rinos.app.api.module.plans.vo.ContractBootstrapResult;
import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.VerificationConsumptionStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;
import br.com.rinos.app.backend.module.identity.vo.VerificationInspectionVO;

@DisplayName("Ativação transacional do cadastro local")
class RegistrationActivationServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("75507575-31b7-41b9-b103-55cdf417a622");
  private static final String PROOF = "opaque-proof";

  private VerificationService verificationService;
  private LegalConsentService legalConsentService;
  private ExternalIdentityService externalIdentityService;
  private IdentityAuditService auditService;
  private RegistrationActivationService service;

  @BeforeEach
  void setUp() {
    verificationService = mock(VerificationService.class);
    legalConsentService = mock(LegalConsentService.class);
    externalIdentityService = mock(ExternalIdentityService.class);
    auditService = mock(IdentityAuditService.class);
    service = new RegistrationActivationService(
        verificationService,
        legalConsentService,
        lifecycleWithPersonalContract(),
        new RegistrationLifecycleService(),
        externalIdentityService,
        auditService,
        new EmailPrivacyService());
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
  void activate_shouldReturnConsentChallengeWithoutConsumingProof_whenLegalVersionChanged() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(verified(registration));
    when(legalConsentService.evaluateRequiredConsents(10L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(1L, 2L), List.of(2L)));

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.CONSENT_REQUIRED);
    assertThat(result.activationReference()).isEqualTo(PROOF);
    assertThat(result.verifiedEmail()).isEqualTo("p***@example.com");
    assertThat(result.legalDocumentIds()).containsExactly("2");
    verify(verificationService, never()).consume(any(), any(), any(), any());
  }

  @Test
  void activate_shouldConsumeAndActivateExactlyOnce_whenLegalConsentsAreCurrent() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(verified(registration));
    when(legalConsentService.evaluateRequiredConsents(10L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(1L, 2L), List.of()));
    when(verificationService.consume(any(), any(), any(), any()))
        .thenReturn(VerificationConsumptionStatusEnum.VERIFIED);

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status()).isEqualTo(RegistrationActivationStatusEnum.ACTIVATED);
    assertThat(registration.getStatus()).isEqualTo(RegistrationStatusEnum.ACTIVE);
    assertThat(registration.getUser().getStatus()).isEqualTo(UserStatusEnum.ACTIVE);
    assertThat(registration.getUser().getActivatedAt()).isEqualTo(NOW);
    verify(verificationService).invalidateAllOpen(20L, NOW);
    verify(externalIdentityService).removePendingForLocalActivation(10L);
  }

  @Test
  void activate_shouldReturnAlreadyActive_whenUsedProofBelongsToCompletedRegistration() {
    RegistrationEntity registration = activeRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.ALREADY_USED,
            registration,
            NOW.plusSeconds(600)));

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.ALREADY_ACTIVE);
    verify(legalConsentService, never()).evaluateRequiredConsents(any(), any());
  }

  /**
   * Mantém a prova expirada separada de uma chave desconhecida e não inicia o lifecycle.
   */
  @Test
  void activate_shouldReturnExpiredProofWithoutLifecycleEffects_whenProofExpired() {
    RegistrationEntity registration = pendingRegistration();
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.EXPIRED,
            registration,
            NOW.minusSeconds(1)));

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.EXPIRED_PROOF);
    verify(legalConsentService, never()).evaluateRequiredConsents(any(), any());
  }

  /**
   * Reconhece o encerramento confirmado pela prova sem reabrir um cadastro cancelado.
   */
  @Test
  void activate_shouldReturnRegistrationClosed_whenValidProofBelongsToCancelledRegistration() {
    RegistrationEntity registration = pendingRegistration();
    registration.setStatus(RegistrationStatusEnum.CANCELLED);
    registration.getUser().setStatus(UserStatusEnum.CANCELLED);
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(verified(registration));

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.REGISTRATION_CLOSED);
    verify(legalConsentService, never()).evaluateRequiredConsents(any(), any());
  }

  /**
   * Aplica a validade absoluta mesmo antes de o job diário materializar o estado expirado.
   */
  @Test
  void activate_shouldReturnRegistrationClosed_whenAbsoluteRegistrationValidityEnded() {
    RegistrationEntity registration = pendingRegistration();
    ReflectionTestUtils.setField(registration, "expiresAt", NOW);
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(verified(registration));

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.REGISTRATION_CLOSED);
    verify(legalConsentService, never()).evaluateRequiredConsents(any(), any());
  }

  @Test
  void completeConsent_shouldRecordCurrentDocumentsBeforeActivating() {
    RegistrationEntity registration = pendingRegistration();
    Map<Long, LegalConsentDecisionEnum> decisions =
        Map.of(1L, LegalConsentDecisionEnum.ACCEPTED);
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(verified(registration));
    when(legalConsentService.validateCurrentAcceptances(List.of(1L), NOW))
        .thenReturn(decisions);
    when(legalConsentService.evaluateRequiredConsents(10L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(1L), List.of()));
    when(verificationService.consume(any(), any(), any(), any()))
        .thenReturn(VerificationConsumptionStatusEnum.VERIFIED);

    RegistrationActivationResultVO result = service.completeConsent(
        PROOF,
        List.of(1L),
        CORRELATION_ID,
        NOW);

    assertThat(result.status()).isEqualTo(RegistrationActivationStatusEnum.ACTIVATED);
    verify(legalConsentService).recordCurrentDecisions(
        registration.getUser(),
        registration,
        decisions,
        NOW);
  }

  @Test
  void activate_shouldRejectWithoutLifecycleEffects_whenProofIsInvalid() {
    when(verificationService.inspect(any(), any(), any()))
        .thenReturn(new VerificationInspectionVO(
            VerificationConsumptionStatusEnum.REJECTED,
            null,
            null));

    RegistrationActivationResultVO result =
        service.activate(PROOF, CORRELATION_ID, NOW);

    assertThat(result.status())
        .isEqualTo(RegistrationActivationStatusEnum.INVALID_PROOF);
    verify(legalConsentService, never()).evaluateRequiredConsents(any(), any());
    verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  private static VerificationInspectionVO verified(
      RegistrationEntity registration) {
    return new VerificationInspectionVO(
        VerificationConsumptionStatusEnum.VERIFIED,
        registration,
        NOW.plusSeconds(600));
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
        NOW.plus(Duration.ofDays(15)));
    ReflectionTestUtils.setField(registration, "id", 20L);
    return registration;
  }

  private static RegistrationEntity activeRegistration() {
    RegistrationEntity registration = pendingRegistration();
    registration.getUser().setStatus(UserStatusEnum.ACTIVE);
    registration.setStatus(RegistrationStatusEnum.ACTIVE);
    return registration;
  }
}
