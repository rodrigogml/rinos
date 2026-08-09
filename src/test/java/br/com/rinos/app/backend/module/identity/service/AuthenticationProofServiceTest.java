package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationProofEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationProofRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationProofInspectionVO;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

@DisplayName("Provas persistentes da autenticação")
class AuthenticationProofServiceTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-08-08T12:00:00Z");

  private AuthenticationFlowRepository flowRepository;
  private AuthenticationProofRepository proofRepository;
  private IdentityAuditService auditService;
  private RFWOpaqueTokenService tokenService;
  private AuthenticationProofService service;
  private AuthenticationKeyringMacService keyringMacService;
  private AuthenticationFlowEntity flow;
  private String reference;

  @BeforeEach
  void setUp() {
    flowRepository = mock(AuthenticationFlowRepository.class);
    proofRepository = mock(AuthenticationProofRepository.class);
    auditService = mock(IdentityAuditService.class);
    keyringMacService = mock(AuthenticationKeyringMacService.class);
    tokenService = new RFWOpaqueTokenService();
    service = new AuthenticationProofService(
        flowRepository,
        proofRepository,
        tokenService,
        auditService,
        keyringMacService);
    reference = tokenService.generate();
    flow = new AuthenticationFlowEntity(
        null,
        tokenService.hash(reference),
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        false,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(300),
        UUID.randomUUID());
    ReflectionTestUtils.setField(flow, "id", 71L);
    when(flowRepository.findByReferenceHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(flow));
  }

  @Test
  void consume_shouldAcceptOnceAndRejectReplay() {
    byte[] digest = new byte[] {7, 8, 9};
    AuthenticationProofEntity proof = proof(digest);
    when(proofRepository.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(
        71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.of(proof));

    AuthenticationProofInspectionVO first = service.consume(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        digest,
        ISSUED_AT.plusSeconds(30));
    AuthenticationProofInspectionVO replay = service.consume(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        digest,
        ISSUED_AT.plusSeconds(40));

    assertThat(first.status()).isEqualTo(AuthenticationOperationStatusEnum.USED);
    assertThat(replay.status()).isEqualTo(AuthenticationOperationStatusEnum.ALREADY_USED);
    assertThat(proof.getStatus()).isEqualTo(AuthenticationProofStatusEnum.USED);
  }

  @Test
  void consume_shouldCountRejectedDigestWithoutConsumingProof() {
    AuthenticationProofEntity proof = proof(new byte[] {1, 2, 3});
    when(proofRepository.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(
        71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.of(proof));

    AuthenticationProofInspectionVO result = service.consume(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        new byte[] {9, 9, 9},
        ISSUED_AT.plusSeconds(30));

    assertThat(result.status()).isEqualTo(AuthenticationOperationStatusEnum.REJECTED);
    assertThat(proof.getAttemptCount()).isEqualTo(1);
    assertThat(flow.getFailureCount()).isEqualTo(1);
    assertThat(proof.getStatus()).isEqualTo(AuthenticationProofStatusEnum.OPEN);
  }

  @Test
  void consumeMac_shouldUsePersistedKeyVersionAndInvalidateAtAttemptLimit() {
    byte[] digest = new byte[32];
    AuthenticationProofEntity proof = new AuthenticationProofEntity(
        flow,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        digest,
        "key-previous",
        ISSUED_AT,
        ISSUED_AT.plusSeconds(120));
    when(proofRepository.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(
        71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.of(proof));
    when(keyringMacService.matches(any(), any(), any())).thenReturn(false);

    AuthenticationProofInspectionVO first = service.consumeMac(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP, "email-otp:71", new byte[] {1}, 2,
        ISSUED_AT.plusSeconds(30));
    AuthenticationProofInspectionVO exhausted = service.consumeMac(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP, "email-otp:71", new byte[] {2}, 2,
        ISSUED_AT.plusSeconds(31));

    assertThat(first.status()).isEqualTo(AuthenticationOperationStatusEnum.REJECTED);
    assertThat(exhausted.status()).isEqualTo(AuthenticationOperationStatusEnum.INVALIDATED);
    assertThat(exhausted.attemptCount()).isEqualTo(2);
    assertThat(proof.getStatus()).isEqualTo(AuthenticationProofStatusEnum.INVALIDATED);
    verify(keyringMacService).matches(
        eq("email-otp:71"),
        aryEq(new byte[] {1}),
        argThat(protectedValue -> protectedValue.keyVersion().equals("key-previous")
            && java.util.Arrays.equals(protectedValue.digest(), digest)));
  }

  @Test
  void cancelIfDigestMatches_shouldNotCancelNewerProof() {
    byte[] currentDigest = new byte[] {7, 8, 9};
    AuthenticationProofEntity proof = proof(currentDigest);
    when(proofRepository.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(
        71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.of(proof));

    AuthenticationProofInspectionVO staleFailure = service.cancelIfDigestMatches(
        reference, AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP, new byte[] {1, 2, 3},
        ISSUED_AT.plusSeconds(30));

    assertThat(staleFailure.status()).isEqualTo(AuthenticationOperationStatusEnum.OPEN);
    assertThat(proof.getStatus()).isEqualTo(AuthenticationProofStatusEnum.OPEN);
  }

  @Test
  void issue_shouldInvalidatePreviousProofBeforePersistingReplacement() {
    AuthenticationProofEntity previous = proof(new byte[] {1});
    when(proofRepository.findByFlowIdAndTypeAndStatusForUpdate(
        71L,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        AuthenticationProofStatusEnum.OPEN)).thenReturn(Optional.of(previous));
    when(proofRepository.saveAndFlush(any(AuthenticationProofEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AuthenticationProofInspectionVO result = service.issue(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        new byte[] {4, 5, 6},
        "key-2",
        ISSUED_AT.plusSeconds(20),
        ISSUED_AT.plusSeconds(120));

    assertThat(previous.getStatus()).isEqualTo(AuthenticationProofStatusEnum.INVALIDATED);
    assertThat(result.status()).isEqualTo(AuthenticationOperationStatusEnum.OPEN);
  }

  @Test
  void consumeValidated_shouldConsumeLegalMarkerOnceWithoutCandidateMaterial() {
    flow = new AuthenticationFlowEntity(
        null,
        tokenService.hash(reference),
        AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        false,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(300),
        UUID.randomUUID());
    ReflectionTestUtils.setField(flow, "id", 71L);
    when(flowRepository.findByReferenceHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(flow));
    AuthenticationProofEntity proof = new AuthenticationProofEntity(
        flow,
        AuthenticationProofTypeEnum.LEGAL_CONSENT,
        new byte[] {1, 2, 3},
        null,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(120));
    when(proofRepository.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(
        71L,
        AuthenticationProofTypeEnum.LEGAL_CONSENT)).thenReturn(Optional.of(proof));

    AuthenticationProofInspectionVO first = service.consumeValidated(
        reference,
        AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
        AuthenticationProofTypeEnum.LEGAL_CONSENT,
        ISSUED_AT.plusSeconds(30));
    AuthenticationProofInspectionVO replay = service.consumeValidated(
        reference,
        AuthenticationFlowPurposeEnum.LEGAL_CONSENT,
        AuthenticationProofTypeEnum.LEGAL_CONSENT,
        ISSUED_AT.plusSeconds(40));

    assertThat(first.status()).isEqualTo(AuthenticationOperationStatusEnum.USED);
    assertThat(replay.status()).isEqualTo(AuthenticationOperationStatusEnum.ALREADY_USED);
    assertThat(proof.getStatus()).isEqualTo(AuthenticationProofStatusEnum.USED);
  }

  private AuthenticationProofEntity proof(byte[] digest) {
    return new AuthenticationProofEntity(
        flow,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        digest,
        null,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(120));
  }
}
