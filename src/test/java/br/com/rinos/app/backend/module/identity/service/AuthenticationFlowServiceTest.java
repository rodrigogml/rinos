package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationProofStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationProofRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

@DisplayName("Fluxos opacos de autenticação")
class AuthenticationFlowServiceTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-08-08T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("6ba789f7-e843-4aa3-939c-d26cf0b1ae6e");

  private AuthenticationFlowRepository flowRepository;
  private AuthenticationFlowMethodRepository methodRepository;
  private AuthenticationProofRepository proofRepository;
  private UserRepository userRepository;
  private IdentityAuditService auditService;
  private RFWOpaqueTokenService tokenService;
  private AuthenticationFlowService service;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    flowRepository = mock(AuthenticationFlowRepository.class);
    methodRepository = mock(AuthenticationFlowMethodRepository.class);
    proofRepository = mock(AuthenticationProofRepository.class);
    userRepository = mock(UserRepository.class);
    auditService = mock(IdentityAuditService.class);
    tokenService = new RFWOpaqueTokenService();
    service = new AuthenticationFlowService(
        flowRepository,
        methodRepository,
        proofRepository,
        userRepository,
        tokenService,
        auditService);
    user = new UserEntity("person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    when(userRepository.findById(41L)).thenReturn(Optional.of(user));
    when(proofRepository.findByFlowIdAndStatusForUpdate(
        any(),
        any(AuthenticationProofStatusEnum.class))).thenReturn(List.of());
  }

  @Test
  void issue_shouldPersistOnlyHashAndReturnRedactedOpaqueReference() {
    when(flowRepository.saveAndFlush(any(AuthenticationFlowEntity.class)))
        .thenAnswer(invocation -> {
          AuthenticationFlowEntity value = invocation.getArgument(0);
          ReflectionTestUtils.setField(value, "id", 51L);
          return value;
        });

    IssuedAuthenticationFlowVO result = service.issue(
        41L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.EMAIL_CODE),
        true,
        ISSUED_AT,
        ISSUED_AT.plusSeconds(300),
        CORRELATION_ID);

    assertThat(result.reference()).hasSize(43);
    assertThat(result.toString()).contains("reference=REDACTED")
        .doesNotContain(result.reference());
    verify(methodRepository).saveAllAndFlush(any());
    verify(auditService).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void consume_shouldUseOnceAndClassifyReplayWithoutChangingPersistentChoice() {
    String reference = tokenService.generate();
    AuthenticationFlowEntity flow = flow(reference, ISSUED_AT.plusSeconds(300));
    AuthenticationFlowMethodEntity method = new AuthenticationFlowMethodEntity(
        flow,
        AuthenticationMethodEnum.TOTP);
    when(flowRepository.findByReferenceHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(flow));
    when(methodRepository.findByFlowIdOrderByMethod(51L)).thenReturn(List.of(method));

    AuthenticationFlowInspectionVO first = service.consume(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        ISSUED_AT.plusSeconds(30));
    AuthenticationFlowInspectionVO replay = service.consume(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        ISSUED_AT.plusSeconds(40));
    AuthenticationFlowInspectionVO crossPurposeReplay = service.consume(
        reference,
        AuthenticationFlowPurposeEnum.FACTOR_RECOVERY,
        ISSUED_AT.plusSeconds(50));

    assertThat(first.status()).isEqualTo(AuthenticationOperationStatusEnum.USED);
    assertThat(first.persistentLoginRequested()).isTrue();
    assertThat(replay.status()).isEqualTo(AuthenticationOperationStatusEnum.ALREADY_USED);
    assertThat(crossPurposeReplay.status()).isEqualTo(AuthenticationOperationStatusEnum.REJECTED);
    assertThat(flow.getStatus()).isEqualTo(AuthenticationFlowStatusEnum.USED);
  }

  @Test
  void inspect_shouldRejectCrossPurposeAndExpireAtBoundary() {
    String reference = tokenService.generate();
    AuthenticationFlowEntity flow = flow(reference, ISSUED_AT.plusSeconds(60));
    when(flowRepository.findByReferenceHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(flow));
    when(methodRepository.findByFlowIdOrderByMethod(51L)).thenReturn(List.of());

    AuthenticationFlowInspectionVO wrongPurpose = service.inspect(
        reference,
        AuthenticationFlowPurposeEnum.FACTOR_RECOVERY,
        ISSUED_AT.plusSeconds(30));
    AuthenticationFlowInspectionVO expired = service.inspect(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        ISSUED_AT.plusSeconds(60));

    assertThat(wrongPurpose.status()).isEqualTo(AuthenticationOperationStatusEnum.REJECTED);
    assertThat(flow.getFailureCount()).isEqualTo(1);
    assertThat(expired.status()).isEqualTo(AuthenticationOperationStatusEnum.EXPIRED);
  }

  @Test
  void verifyMethod_shouldMoveAllowedMethodToVerifiedEvidence() {
    String reference = tokenService.generate();
    AuthenticationFlowEntity flow = flow(reference, ISSUED_AT.plusSeconds(60));
    AuthenticationFlowMethodEntity password = new AuthenticationFlowMethodEntity(
        flow,
        AuthenticationMethodEnum.PASSWORD);
    password.markVerified(ISSUED_AT, null);
    AuthenticationFlowMethodEntity totp = new AuthenticationFlowMethodEntity(
        flow,
        AuthenticationMethodEnum.TOTP);
    when(flowRepository.findByReferenceHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(flow));
    when(methodRepository.findByFlowIdAndMethodForUpdate(
        51L,
        AuthenticationMethodEnum.TOTP)).thenReturn(Optional.of(totp));
    when(methodRepository.findByFlowIdOrderByMethod(51L))
        .thenReturn(List.of(password, totp));

    AuthenticationFlowSnapshotVO result = service.verifyMethod(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.TOTP,
        ISSUED_AT.plusSeconds(10),
        null,
        ISSUED_AT.plusSeconds(10));

    assertThat(result.status()).isEqualTo(AuthenticationOperationStatusEnum.OPEN);
    assertThat(result.permittedMethods()).isEmpty();
    assertThat(result.verifiedMethods())
        .extracting(method -> method.method())
        .containsExactly(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP);
  }

  @Test
  void registerFailure_shouldInvalidateFlowAtSharedLimit() {
    String reference = tokenService.generate();
    AuthenticationFlowEntity flow = flow(reference, ISSUED_AT.plusSeconds(300));
    when(flowRepository.findByReferenceHashForUpdate(any(byte[].class)))
        .thenReturn(Optional.of(flow));

    AuthenticationOperationStatusEnum first = service.registerFailure(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        2,
        ISSUED_AT.plusSeconds(10));
    AuthenticationOperationStatusEnum second = service.registerFailure(
        reference,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        2,
        ISSUED_AT.plusSeconds(20));

    assertThat(first).isEqualTo(AuthenticationOperationStatusEnum.OPEN);
    assertThat(second).isEqualTo(AuthenticationOperationStatusEnum.INVALIDATED);
    assertThat(flow.getFailureCount()).isEqualTo(2);
    assertThat(flow.getStatus()).isEqualTo(AuthenticationFlowStatusEnum.INVALIDATED);
  }

  private AuthenticationFlowEntity flow(String reference, Instant expiresAt) {
    AuthenticationFlowEntity flow = new AuthenticationFlowEntity(
        user,
        tokenService.hash(reference),
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.MULTI_FACTOR,
        true,
        ISSUED_AT,
        expiresAt,
        CORRELATION_ID);
    ReflectionTestUtils.setField(flow, "id", 51L);
    return flow;
  }
}
