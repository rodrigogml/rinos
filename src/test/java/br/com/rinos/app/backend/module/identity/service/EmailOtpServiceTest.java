package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationProofEntity;
import br.com.rinos.app.backend.module.identity.entity.EmailFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailOtpEmissionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationProofRepository;
import br.com.rinos.app.backend.module.identity.repository.EmailFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationProofInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.EmailOtpEmissionDecisionVO;
import br.com.rinos.app.config.AuthenticationKeyringPropertiesConfig;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWOneTimeCodeService;

@DisplayName("OTP por e-mail vinculado ao fluxo")
class EmailOtpServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");
  private static final String FLOW_REFERENCE = "opaque-flow-reference";

  private AuthenticationFlowService flows;
  private AuthenticationProofService proofService;
  private AuthenticationProofRepository proofs;
  private UserRepository users;
  private EmailFactorRepository factors;
  private RFWOneTimeCodeService codes;
  private EmailOtpService service;
  private UserEntity user;
  private EmailFactorEntity factor;

  @BeforeEach
  void setUp() {
    flows = mock(AuthenticationFlowService.class);
    proofService = mock(AuthenticationProofService.class);
    proofs = mock(AuthenticationProofRepository.class);
    users = mock(UserRepository.class);
    factors = mock(EmailFactorRepository.class);
    codes = mock(RFWOneTimeCodeService.class);
    user = new UserEntity("person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 11L);
    factor = new EmailFactorEntity(
        user,
        UUID.fromString("5e09184d-4610-476b-91c6-35760d431813"),
        NOW.minusSeconds(3_600));
    service = new EmailOtpService(
        flows,
        proofService,
        proofs,
        users,
        factors,
        codes,
        keyring(),
        new EmailPrivacyService(),
        properties());
    prepareContext();
  }

  @Test
  void issue_shouldPersistOnlyMacAndReturnEphemeralDispatchMaterial() {
    when(codes.generate()).thenReturn("123456");
    when(proofs.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.empty());
    when(proofs.findIssuedAtByUserIdAndTypeSince(eq(11L),
        eq(AuthenticationProofTypeEnum.EMAIL_OTP), any())).thenReturn(List.of());
    when(proofService.issue(anyString(), eq(AuthenticationFlowPurposeEnum.SIGN_IN),
        eq(AuthenticationProofTypeEnum.EMAIL_OTP), any(byte[].class), eq("v1"), eq(NOW),
        eq(NOW.plusSeconds(300)))).thenReturn(new AuthenticationProofInspectionVO(
            AuthenticationOperationStatusEnum.OPEN,
            AuthenticationProofTypeEnum.EMAIL_OTP,
            0,
            NOW.plusSeconds(300)));

    EmailOtpEmissionDecisionVO result = service.issue(FLOW_REFERENCE, false, NOW);

    assertThat(result.status()).isEqualTo(EmailOtpEmissionStatusEnum.EMITTED);
    assertThat(result.issued().code()).isEqualTo("123456");
    assertThat(result.issued().maskedDestination()).isEqualTo("p***@example.test");
    assertThat(result.issued().toString())
        .contains("code=REDACTED", "proofDigest=REDACTED")
        .doesNotContain("123456", "person@example.test");
    ArgumentCaptor<byte[]> digest = ArgumentCaptor.forClass(byte[].class);
    verify(proofService).issue(eq(FLOW_REFERENCE), eq(AuthenticationFlowPurposeEnum.SIGN_IN),
        eq(AuthenticationProofTypeEnum.EMAIL_OTP), digest.capture(), eq("v1"), eq(NOW),
        eq(NOW.plusSeconds(300)));
    assertThat(digest.getValue()).hasSize(32)
        .isNotEqualTo("123456".getBytes(StandardCharsets.US_ASCII));
  }

  @Test
  void issue_shouldApplyCooldownAndRollingEmissionLimit() {
    AuthenticationProofEntity latest = mock(AuthenticationProofEntity.class);
    when(latest.getIssuedAt()).thenReturn(NOW.minusSeconds(30));
    when(proofs.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.of(latest));

    EmailOtpEmissionDecisionVO cooldown = service.issue(FLOW_REFERENCE, true, NOW);

    assertThat(cooldown.status()).isEqualTo(EmailOtpEmissionStatusEnum.RATE_LIMITED);
    assertThat(cooldown.retryAfter()).isEqualTo(NOW.plusSeconds(30));

    when(latest.getIssuedAt()).thenReturn(NOW.minusSeconds(120));
    when(proofs.findIssuedAtByUserIdAndTypeSince(eq(11L),
        eq(AuthenticationProofTypeEnum.EMAIL_OTP), any())).thenReturn(List.of(
            NOW.minusSeconds(600), NOW.minusSeconds(300), NOW.minusSeconds(120)));

    EmailOtpEmissionDecisionVO window = service.issue(FLOW_REFERENCE, true, NOW);

    assertThat(window.status()).isEqualTo(EmailOtpEmissionStatusEnum.RATE_LIMITED);
    assertThat(window.retryAfter()).isEqualTo(NOW.plusSeconds(300));
  }

  @Test
  void issue_shouldRejectResendWithoutPriorEmission() {
    when(proofs.findFirstByFlowIdAndTypeOrderByIssuedAtDesc(71L,
        AuthenticationProofTypeEnum.EMAIL_OTP)).thenReturn(Optional.empty());

    assertThat(service.issue(FLOW_REFERENCE, true, NOW).status())
        .isEqualTo(EmailOtpEmissionStatusEnum.REJECTED);
  }

  @Test
  void verify_shouldConsumeProofAndRecordFactorUse() {
    when(proofService.consumeMac(eq(FLOW_REFERENCE),
        eq(AuthenticationFlowPurposeEnum.SIGN_IN),
        eq(AuthenticationProofTypeEnum.EMAIL_OTP),
        anyString(), any(byte[].class), eq(5), eq(NOW)))
        .thenReturn(new AuthenticationProofInspectionVO(
            AuthenticationOperationStatusEnum.USED,
            AuthenticationProofTypeEnum.EMAIL_OTP,
            0,
            NOW.plusSeconds(300)));

    EmailOtpVerificationStatusEnum result = service.verify(FLOW_REFERENCE, "123456", NOW);

    assertThat(result).isEqualTo(EmailOtpVerificationStatusEnum.USED);
    assertThat(factor.getLastUsedAt()).isEqualTo(NOW);
  }

  @Test
  void verify_shouldExposeAttemptExhaustionWithoutSecretDetails() {
    when(proofService.consumeMac(anyString(), any(), any(), anyString(), any(), eq(5), eq(NOW)))
        .thenReturn(new AuthenticationProofInspectionVO(
            AuthenticationOperationStatusEnum.INVALIDATED,
            AuthenticationProofTypeEnum.EMAIL_OTP,
            5,
            NOW.plusSeconds(300)));

    assertThat(service.verify(FLOW_REFERENCE, "000000", NOW))
        .isEqualTo(EmailOtpVerificationStatusEnum.ATTEMPTS_EXHAUSTED);
  }

  @Test
  void invalidateFailedDelivery_shouldCancelOnlyTheExpectedDigest() {
    byte[] digest = new byte[32];
    Arrays.fill(digest, (byte) 3);

    service.invalidateFailedDelivery(FLOW_REFERENCE, digest, NOW.plusSeconds(2));

    verify(proofService).cancelIfDigestMatches(
        FLOW_REFERENCE,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationProofTypeEnum.EMAIL_OTP,
        digest,
        NOW.plusSeconds(2));
  }

  private void prepareContext() {
    when(flows.resolveUserId(FLOW_REFERENCE)).thenReturn(Optional.of(11L));
    when(users.findByIdForUpdate(11L)).thenReturn(Optional.of(user));
    when(flows.snapshot(FLOW_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot());
    when(factors.findByUserIdForUpdate(11L)).thenReturn(Optional.of(factor));
  }

  private static AuthenticationFlowSnapshotVO snapshot() {
    return new AuthenticationFlowSnapshotVO(
        AuthenticationOperationStatusEnum.OPEN,
        71L,
        11L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.EMAIL_CODE),
        List.of(),
        false,
        NOW.plusSeconds(300),
        UUID.fromString("e1db5d63-aeb5-4b5a-a450-1a751988cf2b"));
  }

  private static AuthenticationMfaPropertiesConfig properties() {
    return new AuthenticationMfaPropertiesConfig(
        Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15));
  }

  private static AuthenticationKeyringMacService keyring() {
    byte[] key = new byte[32];
    Arrays.fill(key, (byte) 7);
    return new AuthenticationKeyringMacService(new AuthenticationKeyringService(
        new AuthenticationKeyringPropertiesConfig(
            true, "v1", Map.of("v1", Base64.getEncoder().encodeToString(key)))));
  }
}
