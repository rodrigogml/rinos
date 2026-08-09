package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.LegalConsentDecisionEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationProofInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;

@DisplayName("Orquestrador único de autenticação")
class AuthenticationOrchestrationServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
  private static final Instant EXPIRES_AT = NOW.plusSeconds(300);
  private static final UUID CORRELATION_ID =
      UUID.fromString("b345bb0a-7f6d-444d-93d0-a4ce53aac282");
  private static final String SIGN_IN_REFERENCE = "sign-in-reference";

  private AuthenticationFlowService flowService;
  private AuthenticationProofService proofService;
  private AuthenticationMethodAvailabilityService methodAvailability;
  private LegalConsentService legalConsentService;
  private UserRepository userRepository;
  private AuthenticationOrchestrationService service;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    flowService = mock(AuthenticationFlowService.class);
    proofService = mock(AuthenticationProofService.class);
    methodAvailability = mock(AuthenticationMethodAvailabilityService.class);
    legalConsentService = mock(LegalConsentService.class);
    userRepository = mock(UserRepository.class);
    service = new AuthenticationOrchestrationService(
        flowService,
        proofService,
        new AuthenticationAssurancePolicyService(),
        methodAvailability,
        legalConsentService,
        userRepository);
    user = user(UserStatusEnum.ACTIVE);
    when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
    when(methodAvailability.availableMethods(41L))
        .thenReturn(Set.of(AuthenticationMethodEnum.values()));
    when(proofService.issue(
        any(), any(), any(), any(byte[].class), any(), any(), any()))
        .thenReturn(new AuthenticationProofInspectionVO(
            AuthenticationOperationStatusEnum.OPEN,
            AuthenticationProofTypeEnum.LEGAL_CONSENT,
            0,
            EXPIRES_AT));
  }

  @Test
  void start_shouldRequireAnIndependentSecondFactorWithoutConsultingLegalGate() {
    stubSignInIssueAndSnapshot(AuthenticationAssuranceEnum.MULTI_FACTOR, snapshot(
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.TOTP),
        List.of(verified(AuthenticationMethodEnum.PASSWORD))));

    AuthenticationOrchestrationDecisionVO result = start(
        AuthenticationAssuranceEnum.MULTI_FACTOR);

    assertThat(result.status())
        .isEqualTo(AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED);
    assertThat(result.continuationReference()).isEqualTo(SIGN_IN_REFERENCE);
    assertThat(result.permittedMethods()).containsExactly(AuthenticationMethodEnum.TOTP);
    assertThat(result.userId()).isNull();
    verify(legalConsentService, never()).evaluateRequiredConsents(41L, NOW);
  }

  @Test
  void start_shouldMoveVerifiedEvidenceToAnOpaqueLegalContinuation() {
    stubSignInIssueAndSnapshot(AuthenticationAssuranceEnum.SINGLE_FACTOR, snapshot(
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(verified(AuthenticationMethodEnum.PASSWORD))));
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(7L, 8L), List.of(8L)));
    when(flowService.cancel(SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(flowResult(AuthenticationOperationStatusEnum.INVALIDATED));
    when(flowService.issue(
        eq(41L),
        eq(AuthenticationFlowPurposeEnum.LEGAL_CONSENT),
        eq(AuthenticationMethodEnum.PASSWORD),
        eq(AuthenticationAssuranceEnum.SINGLE_FACTOR),
        anySet(),
        anyList(),
        eq(false),
        eq(NOW),
        eq(EXPIRES_AT),
        eq(CORRELATION_ID)))
        .thenReturn(new IssuedAuthenticationFlowVO(
            "legal-reference", EXPIRES_AT, CORRELATION_ID));

    AuthenticationOrchestrationDecisionVO result = start(
        AuthenticationAssuranceEnum.SINGLE_FACTOR);

    assertThat(result.status())
        .isEqualTo(AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED);
    assertThat(result.continuationReference()).isEqualTo("legal-reference");
    assertThat(result.missingLegalDocumentIds()).containsExactly(8L);
    assertThat(result.userId()).isNull();
    verify(flowService).cancel(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW);
    verify(proofService).issue(
        eq("legal-reference"),
        eq(AuthenticationFlowPurposeEnum.LEGAL_CONSENT),
        eq(AuthenticationProofTypeEnum.LEGAL_CONSENT),
        any(byte[].class),
        isNull(),
        eq(NOW),
        eq(EXPIRES_AT));
  }

  @Test
  void completeLegalConsent_shouldRecordAllCurrentRequiredAndConsumeMarkerAtomically() {
    String reference = "legal-reference";
    when(flowService.resolveUserId(reference)).thenReturn(Optional.of(41L));
    when(flowService.snapshot(reference, AuthenticationFlowPurposeEnum.LEGAL_CONSENT, NOW))
        .thenReturn(legalSnapshot());
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(7L, 8L), List.of(8L)));
    when(proofService.consumeValidated(
        reference,
        AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
        AuthenticationProofTypeEnum.LEGAL_CONSENT,
        NOW))
        .thenReturn(new AuthenticationProofInspectionVO(
            AuthenticationOperationStatusEnum.USED,
            AuthenticationProofTypeEnum.LEGAL_CONSENT,
            0,
            EXPIRES_AT));

    AuthenticationOrchestrationDecisionVO result = service.completeLegalConsent(
        reference, Set.of(8L), NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.READY);
    assertThat(result.continuationReference()).isEqualTo(reference);
    assertThat(result.userId()).isEqualTo(41L);
    verify(legalConsentService).recordCurrentDecisions(
        eq(user),
        isNull(),
        eq(java.util.Map.of(
            7L, LegalConsentDecisionEnum.ACCEPTED,
            8L, LegalConsentDecisionEnum.ACCEPTED)),
        eq(NOW));
    verify(flowService, never()).consume(
        reference, AuthenticationFlowPurposeEnum.LEGAL_CONSENT, NOW);
  }

  @Test
  void completeLegalConsent_shouldRefreshChallengeWithoutRecording_whenCatalogSelectionIsStale() {
    String reference = "legal-reference";
    when(flowService.resolveUserId(reference)).thenReturn(Optional.of(41L));
    when(flowService.snapshot(reference, AuthenticationFlowPurposeEnum.LEGAL_CONSENT, NOW))
        .thenReturn(legalSnapshot());
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(7L, 9L), List.of(9L)));

    AuthenticationOrchestrationDecisionVO result = service.completeLegalConsent(
        reference, Set.of(8L), NOW);

    assertThat(result.status())
        .isEqualTo(AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED);
    assertThat(result.missingLegalDocumentIds()).containsExactly(9L);
    verify(proofService).issue(
        eq(reference),
        eq(AuthenticationFlowPurposeEnum.LEGAL_CONSENT),
        eq(AuthenticationProofTypeEnum.LEGAL_CONSENT),
        any(byte[].class),
        isNull(),
        eq(NOW),
        eq(EXPIRES_AT));
    verify(proofService, never()).consumeValidated(any(), any(), any(), any());
    verify(legalConsentService, never()).recordCurrentDecisions(any(), any(), any(), any());
  }

  @Test
  void complete_shouldLeaveReadyFlowOpenForSessionLifecycle() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(flowService.snapshot(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(
            AuthenticationAssuranceEnum.SINGLE_FACTOR,
            Set.of(),
            List.of(verified(AuthenticationMethodEnum.PASSWORD))));
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(7L, 8L), List.of()));
    AuthenticationOrchestrationDecisionVO result = service.complete(SIGN_IN_REFERENCE, NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.READY);
    assertThat(result.continuationReference()).isEqualTo(SIGN_IN_REFERENCE);
    assertThat(result.userId()).isEqualTo(41L);
    assertThat(result.email()).isEqualTo("person@example.test");
    verify(flowService, never()).consume(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW);
  }

  @Test
  void advance_shouldRejectBlockedOwnerBeforeConsumingFactorEvidence() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(userRepository.findByIdForUpdate(41L))
        .thenReturn(Optional.of(user(UserStatusEnum.BLOCKED)));

    AuthenticationOrchestrationDecisionVO result = service.advance(
        SIGN_IN_REFERENCE,
        AuthenticationMethodEnum.TOTP,
        NOW,
        null,
        NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(flowService, never()).verifyMethod(
        eq(SIGN_IN_REFERENCE),
        eq(AuthenticationFlowPurposeEnum.SIGN_IN),
        eq(AuthenticationMethodEnum.TOTP),
        eq(NOW),
        isNull(),
        eq(NOW));
  }

  @Test
  void complete_shouldRejectWhenVerifiedMethodWasRevokedAfterProof() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(flowService.snapshot(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(
            AuthenticationAssuranceEnum.SINGLE_FACTOR,
            Set.of(),
            List.of(verified(AuthenticationMethodEnum.PASSWORD))));
    when(methodAvailability.availableMethods(41L)).thenReturn(Set.of());

    AuthenticationOrchestrationDecisionVO result = service.complete(SIGN_IN_REFERENCE, NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(legalConsentService, never()).evaluateRequiredConsents(41L, NOW);
  }

  @Test
  void start_shouldNotOfferFactorThatIsNoLongerAvailable() {
    stubSignInIssueAndSnapshot(AuthenticationAssuranceEnum.MULTI_FACTOR, snapshot(
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.TOTP),
        List.of(verified(AuthenticationMethodEnum.PASSWORD))));
    when(methodAvailability.availableMethods(41L))
        .thenReturn(Set.of(AuthenticationMethodEnum.PASSWORD));

    AuthenticationOrchestrationDecisionVO result = start(
        AuthenticationAssuranceEnum.MULTI_FACTOR);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.CONFLICT);
    assertThat(result.permittedMethods()).isEmpty();
  }

  @Test
  void advance_shouldReachReadyAfterIndependentSecondFactor() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(flowService.verifyMethod(
        SIGN_IN_REFERENCE,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.TOTP,
        NOW,
        null,
        NOW))
        .thenReturn(snapshot(
            AuthenticationAssuranceEnum.MULTI_FACTOR,
            Set.of(AuthenticationMethodEnum.TOTP),
            List.of(
                verified(AuthenticationMethodEnum.PASSWORD),
                verified(AuthenticationMethodEnum.TOTP))));
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(), List.of()));

    AuthenticationOrchestrationDecisionVO result = service.advance(
        SIGN_IN_REFERENCE,
        AuthenticationMethodEnum.TOTP,
        NOW,
        null,
        NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.READY);
    assertThat(result.verifiedMethods())
        .extracting(AuthenticationFlowVerifiedMethodVO::method)
        .containsExactly(
            AuthenticationMethodEnum.PASSWORD,
            AuthenticationMethodEnum.TOTP);
  }

  @Test
  void complete_shouldFailClosedWhenLegalCatalogIsUnavailable() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(flowService.snapshot(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(
            AuthenticationAssuranceEnum.SINGLE_FACTOR,
            Set.of(),
            List.of(verified(AuthenticationMethodEnum.PASSWORD))));
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenThrow(new IllegalStateException("catalog unavailable"));

    AuthenticationOrchestrationDecisionVO result = service.complete(SIGN_IN_REFERENCE, NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.UNAVAILABLE);
    assertThat(result.userId()).isNull();
    assertThat(result.continuationReference()).isEqualTo(SIGN_IN_REFERENCE);
  }

  @Test
  void complete_shouldMapExpiredAndConsumedFlowsWithoutPartialPrincipal() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(flowService.snapshot(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(terminalSnapshot(AuthenticationOperationStatusEnum.EXPIRED))
        .thenReturn(terminalSnapshot(AuthenticationOperationStatusEnum.ALREADY_USED));

    AuthenticationOrchestrationDecisionVO expired = service.complete(SIGN_IN_REFERENCE, NOW);
    AuthenticationOrchestrationDecisionVO repeated = service.complete(SIGN_IN_REFERENCE, NOW);

    assertThat(expired.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.EXPIRED);
    assertThat(expired.userId()).isNull();
    assertThat(repeated.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.CONFLICT);
    assertThat(repeated.userId()).isNull();
    verify(legalConsentService, never()).evaluateRequiredConsents(41L, NOW);
  }

  @Test
  void cancel_shouldFallbackToLegalFlowAndRemainIdempotent() {
    when(flowService.resolveUserId(SIGN_IN_REFERENCE)).thenReturn(Optional.of(41L));
    when(flowService.cancel(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(flowResult(AuthenticationOperationStatusEnum.REJECTED));
    when(flowService.cancel(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.LEGAL_CONSENT, NOW))
        .thenReturn(flowResult(AuthenticationOperationStatusEnum.INVALIDATED))
        .thenReturn(flowResult(AuthenticationOperationStatusEnum.ALREADY_USED));

    AuthenticationOrchestrationDecisionVO first = service.cancel(SIGN_IN_REFERENCE, NOW);
    AuthenticationOrchestrationDecisionVO repeated = service.cancel(SIGN_IN_REFERENCE, NOW);

    assertThat(first.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.CANCELLED);
    assertThat(repeated.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.CANCELLED);
  }

  @Test
  void start_shouldRejectInactiveUserWithoutCreatingFlow() {
    when(userRepository.findByIdForUpdate(41L))
        .thenReturn(Optional.of(user(UserStatusEnum.BLOCKED)));

    AuthenticationOrchestrationDecisionVO result = start(
        AuthenticationAssuranceEnum.SINGLE_FACTOR);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(flowService, never()).issue(
        eq(41L),
        eq(AuthenticationFlowPurposeEnum.SIGN_IN),
        eq(AuthenticationMethodEnum.PASSWORD),
        eq(AuthenticationAssuranceEnum.SINGLE_FACTOR),
        anySet(),
        anyList(),
        eq(false),
        eq(NOW),
        eq(EXPIRES_AT),
        eq(CORRELATION_ID));
  }

  private AuthenticationOrchestrationDecisionVO start(
      AuthenticationAssuranceEnum requiredAssurance) {
    return service.start(
        41L,
        AuthenticationMethodEnum.PASSWORD,
        requiredAssurance,
        Set.of(AuthenticationMethodEnum.TOTP),
        false,
        NOW,
        null,
        NOW,
        EXPIRES_AT,
        CORRELATION_ID);
  }

  private void stubSignInIssueAndSnapshot(
      AuthenticationAssuranceEnum requiredAssurance,
      AuthenticationFlowSnapshotVO snapshot) {
    when(flowService.issue(
        eq(41L),
        eq(AuthenticationFlowPurposeEnum.SIGN_IN),
        eq(AuthenticationMethodEnum.PASSWORD),
        eq(requiredAssurance),
        anySet(),
        anyList(),
        eq(false),
        eq(NOW),
        eq(EXPIRES_AT),
        eq(CORRELATION_ID)))
        .thenReturn(new IssuedAuthenticationFlowVO(
            SIGN_IN_REFERENCE, EXPIRES_AT, CORRELATION_ID));
    when(flowService.snapshot(
        SIGN_IN_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot);
  }

  private static AuthenticationFlowSnapshotVO snapshot(
      AuthenticationAssuranceEnum requiredAssurance,
      Set<AuthenticationMethodEnum> permittedMethods,
      List<AuthenticationFlowVerifiedMethodVO> verifiedMethods) {
    return new AuthenticationFlowSnapshotVO(
        AuthenticationOperationStatusEnum.OPEN,
        91L,
        41L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        requiredAssurance,
        permittedMethods,
        verifiedMethods,
        false,
        EXPIRES_AT,
        CORRELATION_ID);
  }

  private static AuthenticationFlowSnapshotVO legalSnapshot() {
    return new AuthenticationFlowSnapshotVO(
        AuthenticationOperationStatusEnum.OPEN,
        92L,
        41L,
        AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(verified(AuthenticationMethodEnum.PASSWORD)),
        false,
        EXPIRES_AT,
        CORRELATION_ID);
  }

  private static AuthenticationFlowVerifiedMethodVO verified(AuthenticationMethodEnum method) {
    return new AuthenticationFlowVerifiedMethodVO(method, NOW, null);
  }

  private static AuthenticationFlowSnapshotVO terminalSnapshot(
      AuthenticationOperationStatusEnum status) {
    return new AuthenticationFlowSnapshotVO(
        status,
        91L,
        41L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(),
        false,
        EXPIRES_AT,
        CORRELATION_ID);
  }

  private static AuthenticationFlowInspectionVO flowResult(
      AuthenticationOperationStatusEnum status) {
    return new AuthenticationFlowInspectionVO(
        status,
        41L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        false,
        EXPIRES_AT,
        CORRELATION_ID);
  }

  private static UserEntity user(UserStatusEnum status) {
    UserEntity value = new UserEntity("person@example.test", "person@example.test", status);
    ReflectionTestUtils.setField(value, "id", 41L);
    return value;
  }
}
