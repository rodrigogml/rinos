package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailOtpVerificationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationOrchestrationDecisionVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

@DisplayName("Composição transacional do segundo fator")
class SecondFactorServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final String REFERENCE = "opaque-flow";
  private AuthenticationFlowService flows;
  private AuthenticationMethodAvailabilityService availability;
  private TotpFactorService totp;
  private EmailOtpService email;
  private RecoveryCodeService recovery;
  private AuthenticationOrchestrationService orchestration;
  private SecondFactorService service;

  @BeforeEach
  void setUp() {
    flows = mock(AuthenticationFlowService.class);
    availability = mock(AuthenticationMethodAvailabilityService.class);
    totp = mock(TotpFactorService.class);
    email = mock(EmailOtpService.class);
    recovery = mock(RecoveryCodeService.class);
    orchestration = mock(AuthenticationOrchestrationService.class);
    UserRepository users = mock(UserRepository.class);
    UserEntity user = mock(UserEntity.class);
    when(user.getId()).thenReturn(41L);
    when(user.getStatus()).thenReturn(UserStatusEnum.ACTIVE);
    when(flows.resolveUserId(REFERENCE)).thenReturn(java.util.Optional.of(41L));
    when(users.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
    when(flows.snapshot(REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(AuthenticationMethodEnum.PASSWORD,
            Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.EMAIL_CODE,
                AuthenticationMethodEnum.RECOVERY_CODE)));
    when(availability.availableMethods(41L)).thenReturn(Set.of(
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationMethodEnum.TOTP,
        AuthenticationMethodEnum.EMAIL_CODE,
        AuthenticationMethodEnum.RECOVERY_CODE));
    when(orchestration.advance(eq(REFERENCE), any(), eq(NOW), eq(null), eq(NOW)))
        .thenReturn(ready());
    service = new SecondFactorService(
        flows, availability, new AuthenticationSecondFactorPolicyService(),
        totp, email, recovery, orchestration, users,
        new AuthenticationMfaPropertiesConfig(
            Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)));
  }

  @Test
  void verify_shouldAdvanceFlowAfterValidTotp() {
    when(totp.verifyActive(41L, "123456", NOW)).thenReturn(FactorOperationStatusEnum.USED);

    AuthenticationOrchestrationDecisionVO result = service.verify(
        REFERENCE, AuthenticationMethodEnum.TOTP, "123456", NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.READY);
    verify(orchestration).advance(REFERENCE, AuthenticationMethodEnum.TOTP, NOW, null, NOW);
    verify(flows, never()).registerFailure(any(), any(), any(Integer.class), any());
  }

  @Test
  void verify_shouldTreatLastRecoveryCodeAsSuccessfulProof() {
    when(recovery.consume(41L, "LAST-CODE", NOW))
        .thenReturn(FactorOperationStatusEnum.EXHAUSTED);

    AuthenticationOrchestrationDecisionVO result = service.verify(
        REFERENCE, AuthenticationMethodEnum.RECOVERY_CODE, "LAST-CODE", NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.READY);
    verify(orchestration).advance(
        REFERENCE, AuthenticationMethodEnum.RECOVERY_CODE, NOW, null, NOW);
  }

  @Test
  void verify_shouldRejectFactorRevokedAfterChallengeWithoutConsumingProof() {
    when(availability.availableMethods(41L)).thenReturn(Set.of(AuthenticationMethodEnum.PASSWORD));

    AuthenticationOrchestrationDecisionVO result = service.verify(
        REFERENCE, AuthenticationMethodEnum.TOTP, "123456", NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(totp, never()).verifyActive(any(), any(), any());
    verify(orchestration, never()).advance(any(), any(), any(), any(), any());
  }

  @Test
  void verify_shouldRejectEmailAsSameChannelAfterGoogle() {
    when(flows.snapshot(REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(AuthenticationMethodEnum.GOOGLE,
            Set.of(AuthenticationMethodEnum.EMAIL_CODE, AuthenticationMethodEnum.TOTP)));

    AuthenticationOrchestrationDecisionVO result = service.verify(
        REFERENCE, AuthenticationMethodEnum.EMAIL_CODE, "123456", NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(email, never()).verify(any(), any(), any());
  }

  @Test
  void verify_shouldCountRejectedTotpAgainstSharedFlowLimit() {
    when(totp.verifyActive(41L, "000000", NOW)).thenReturn(FactorOperationStatusEnum.REJECTED);

    AuthenticationOrchestrationDecisionVO result = service.verify(
        REFERENCE, AuthenticationMethodEnum.TOTP, "000000", NOW);

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(flows).registerFailure(
        REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, 5, NOW);
    verify(orchestration, never()).advance(any(), any(), any(), any(), any());
  }

  @Test
  void verify_shouldNotDoubleCountRejectedEmailProof() {
    when(email.verify(REFERENCE, "000000", NOW))
        .thenReturn(EmailOtpVerificationStatusEnum.REJECTED);

    service.verify(REFERENCE, AuthenticationMethodEnum.EMAIL_CODE, "000000", NOW);

    verify(flows).enforceFailureLimit(
        REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, 5, NOW);
    verify(flows, never()).registerFailure(any(), any(), any(Integer.class), any());
  }

  private static AuthenticationFlowSnapshotVO snapshot(
      AuthenticationMethodEnum primary,
      Set<AuthenticationMethodEnum> permitted) {
    return new AuthenticationFlowSnapshotVO(
        AuthenticationOperationStatusEnum.OPEN,
        71L,
        41L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        primary,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        permitted,
        List.of(new AuthenticationFlowVerifiedMethodVO(primary, NOW, null)),
        false,
        NOW.plusSeconds(300),
        UUID.fromString("17db3ddc-c405-48ef-87dc-9e85e910fb52"));
  }

  private static AuthenticationOrchestrationDecisionVO ready() {
    return new AuthenticationOrchestrationDecisionVO(
        AuthenticationOrchestrationStatusEnum.READY,
        REFERENCE,
        41L,
        "person@example.test",
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(),
        List.of(),
        Set.of(),
        false,
        NOW.plusSeconds(300),
        UUID.fromString("17db3ddc-c405-48ef-87dc-9e85e910fb52"));
  }
}
