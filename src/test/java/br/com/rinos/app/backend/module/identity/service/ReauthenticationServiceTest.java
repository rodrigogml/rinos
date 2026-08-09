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
import org.junit.jupiter.api.Test;

import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.ReauthenticationContextEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.ReauthenticationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.repository.ReauthenticationContextRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedAuthenticationFlowVO;
import br.com.rinos.app.backend.module.identity.vo.ReauthenticationDecisionVO;
import br.com.rinos.app.backend.module.identity.vo.VerifiedReauthenticationProofVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;

/**
 * Verifica o vínculo e o consumo da retomada de uma operação sensível.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
class ReauthenticationServiceTest {

  private static final Long USER_ID = 7L;
  private static final Long SESSION_ID = 11L;
  private static final Long FLOW_ID = 13L;
  private static final Instant AUTHENTICATED_AT = Instant.parse("2026-08-09T12:00:00Z");
  private static final Instant NOW = AUTHENTICATED_AT.plus(Duration.ofMinutes(20));
  private static final UUID SESSION_REFERENCE =
      UUID.fromString("d0306678-4a8a-4a9d-a622-6855186cb0ed");
  private static final UUID CORRELATION_ID =
      UUID.fromString("09dcaa16-38d9-4817-ab5a-da8f14165eb8");
  private static final String CHALLENGE_REFERENCE = "opaque-reauthentication";

  private UserRepository userRepository;
  private AuthSessionRepository sessionRepository;
  private AuthSessionMethodRepository sessionMethodRepository;
  private AuthenticationFlowRepository flowRepository;
  private ReauthenticationContextRepository contextRepository;
  private AuthenticationFlowService flowService;
  private AuthenticationMethodAvailabilityService availabilityService;
  private ReauthenticationProofService proofService;
  private IdentityAuditService auditService;
  private IdentityReferenceService referenceService;
  private ReauthenticationService service;
  private UserEntity user;
  private AuthSessionEntity session;

  /** Prepara dependências isoladas e políticas reais para cada cenário. */
  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    sessionRepository = mock(AuthSessionRepository.class);
    sessionMethodRepository = mock(AuthSessionMethodRepository.class);
    flowRepository = mock(AuthenticationFlowRepository.class);
    contextRepository = mock(ReauthenticationContextRepository.class);
    flowService = mock(AuthenticationFlowService.class);
    availabilityService = mock(AuthenticationMethodAvailabilityService.class);
    proofService = mock(ReauthenticationProofService.class);
    auditService = mock(IdentityAuditService.class);
    referenceService = new IdentityReferenceService();
    user = mock(UserEntity.class);
    session = mock(AuthSessionEntity.class);
    when(user.getId()).thenReturn(USER_ID);
    when(user.getStatus()).thenReturn(UserStatusEnum.ACTIVE);
    when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    when(session.getId()).thenReturn(SESSION_ID);
    when(session.getUser()).thenReturn(user);
    when(session.getPublicReference()).thenReturn(referenceService.encode(SESSION_REFERENCE));
    when(session.getStatus()).thenReturn(AuthSessionStatusEnum.ACTIVE);
    when(session.getAssuranceLevel()).thenReturn(AuthenticationAssuranceEnum.SINGLE_FACTOR);
    when(session.getLastStrongAuthAt()).thenReturn(AUTHENTICATED_AT);
    when(session.getAbsoluteExpiresAt()).thenReturn(NOW.plus(Duration.ofHours(1)));
    when(session.getIdleExpiresAt()).thenReturn(NOW.plus(Duration.ofMinutes(30)));
    when(sessionRepository.findByUserIdAndPublicReferenceForUpdate(
        USER_ID, referenceService.encode(SESSION_REFERENCE))).thenReturn(Optional.of(session));
    when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
    AuthSessionMethodEntity sessionMethod = mock(AuthSessionMethodEntity.class);
    when(sessionMethod.getMethod()).thenReturn(AuthenticationMethodEnum.PASSWORD);
    when(sessionMethod.getFactorOrder()).thenReturn((short) 1);
    when(sessionMethod.getVerifiedAt()).thenReturn(AUTHENTICATED_AT);
    when(sessionMethodRepository.findBySessionIdOrderByFactorOrder(SESSION_ID))
        .thenReturn(List.of(sessionMethod));
    when(availabilityService.availableMethods(USER_ID))
        .thenReturn(Set.of(AuthenticationMethodEnum.PASSWORD));
    when(proofService.supportedMethods()).thenReturn(Set.of(AuthenticationMethodEnum.PASSWORD));
    when(proofService.verify(
        USER_ID, AuthenticationMethodEnum.PASSWORD, "CorrectPassword1!", NOW))
        .thenReturn(Optional.of(new VerifiedReauthenticationProofVO(
            AuthenticationMethodEnum.PASSWORD, null)));
    AuthenticationAssurancePolicyService assurancePolicy =
        new AuthenticationAssurancePolicyService();
    ReauthenticationPolicyService reauthenticationPolicy = new ReauthenticationPolicyService(
        assurancePolicy,
        new AuthenticationSessionPropertiesConfig(
            Duration.ofHours(12),
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            Duration.ofDays(7),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            "RINOS_AUTH",
            true,
            "Strict"));
    service = new ReauthenticationService(
        userRepository,
        sessionRepository,
        sessionMethodRepository,
        flowRepository,
        contextRepository,
        flowService,
        availabilityService,
        proofService,
        assurancePolicy,
        reauthenticationPolicy,
        referenceService,
        auditService,
        new AuthenticationMfaPropertiesConfig(
            Duration.ofMinutes(5), 5, 6, Duration.ofSeconds(30), 1, 10));
  }

  @Test
  void begin_shouldPersistSessionAndOperationBinding_whenStrongAuthenticationIsStale() {
    IssuedAuthenticationFlowVO issued = new IssuedAuthenticationFlowVO(
        CHALLENGE_REFERENCE, NOW.plus(Duration.ofMinutes(5)), CORRELATION_ID);
    when(flowService.issue(
        eq(USER_ID),
        eq(AuthenticationFlowPurposeEnum.REAUTHENTICATION),
        eq(null),
        eq(AuthenticationAssuranceEnum.SINGLE_FACTOR),
        eq(Set.of(AuthenticationMethodEnum.PASSWORD)),
        eq(false),
        eq(NOW),
        eq(NOW.plus(Duration.ofMinutes(5))),
        any(UUID.class))).thenReturn(issued);
    when(flowService.snapshot(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW))
        .thenReturn(openSnapshot(List.of()));
    when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(mock(AuthenticationFlowEntity.class));

    ReauthenticationDecisionVO result = service.begin(
        USER_ID,
        SESSION_REFERENCE,
        ReauthenticationOperationEnum.CHANGE_PASSWORD,
        NOW);

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.CHALLENGE_REQUIRED);
    assertThat(result.challengeReference()).isEqualTo(CHALLENGE_REFERENCE);
    assertThat(result.allowedMethods()).containsExactly(AuthenticationMethodEnum.PASSWORD);
    verify(contextRepository).saveAndFlush(any(ReauthenticationContextEntity.class));
  }

  @Test
  void complete_shouldConsumeOnceAndRefreshOnlyBoundSession_whenProofIsValid() {
    ReauthenticationContextEntity context = mock(ReauthenticationContextEntity.class);
    when(context.getAuthSession()).thenReturn(session);
    when(context.getOperation()).thenReturn(ReauthenticationOperationEnum.CHANGE_PASSWORD);
    when(contextRepository.findByAuthenticationFlowIdForUpdate(FLOW_ID))
        .thenReturn(Optional.of(context));
    when(flowService.resolveUserId(CHALLENGE_REFERENCE)).thenReturn(Optional.of(USER_ID));
    AuthenticationFlowSnapshotVO open = openSnapshot(List.of());
    AuthenticationFlowSnapshotVO verified = openSnapshot(List.of(
        new AuthenticationFlowVerifiedMethodVO(AuthenticationMethodEnum.PASSWORD, NOW, null)));
    when(flowService.snapshot(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW))
        .thenReturn(open);
    when(flowService.verifyMethod(
        CHALLENGE_REFERENCE,
        AuthenticationFlowPurposeEnum.REAUTHENTICATION,
        AuthenticationMethodEnum.PASSWORD,
        NOW,
        null,
        NOW)).thenReturn(verified);
    when(flowService.consume(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW))
        .thenReturn(inspection(AuthenticationOperationStatusEnum.USED));

    ReauthenticationDecisionVO result = service.complete(
        USER_ID,
        SESSION_REFERENCE,
        CHALLENGE_REFERENCE,
        AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW);

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.COMPLETED);
    verify(session).recordStrongAuthentication(AuthenticationAssuranceEnum.SINGLE_FACTOR, NOW);
    verify(sessionMethodRepository).saveAndFlush(any(AuthSessionMethodEntity.class));
    verify(flowService).consume(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW);
  }

  @Test
  void complete_shouldRejectDifferentCurrentSession_withoutConsumingChallenge() {
    ReauthenticationContextEntity context = mock(ReauthenticationContextEntity.class);
    when(context.getAuthSession()).thenReturn(session);
    when(context.getOperation()).thenReturn(ReauthenticationOperationEnum.CHANGE_PASSWORD);
    when(contextRepository.findByAuthenticationFlowIdForUpdate(FLOW_ID))
        .thenReturn(Optional.of(context));
    when(flowService.resolveUserId(CHALLENGE_REFERENCE)).thenReturn(Optional.of(USER_ID));
    when(flowService.snapshot(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW))
        .thenReturn(openSnapshot(List.of()));

    ReauthenticationDecisionVO result = service.complete(
        USER_ID,
        UUID.fromString("dc5f0af9-1ac5-4ca2-a784-230336f9de1e"),
        CHALLENGE_REFERENCE,
        AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW);

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.ACCESS_DENIED);
    verify(flowService, never()).consume(any(), any(), any());
    verify(session, never()).recordStrongAuthentication(any(), any());
  }

  @Test
  void complete_shouldRejectInvalidProofWithoutInspectingOrMutatingFlow() {
    when(proofService.verify(
        USER_ID, AuthenticationMethodEnum.PASSWORD, "WrongPassword1!", NOW))
        .thenReturn(Optional.empty());

    ReauthenticationDecisionVO result = service.complete(
        USER_ID,
        SESSION_REFERENCE,
        CHALLENGE_REFERENCE,
        AuthenticationMethodEnum.PASSWORD,
        "WrongPassword1!",
        NOW);

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.REJECTED);
    verify(flowService, never()).resolveUserId(any());
    verify(flowService, never()).consume(any(), any(), any());
    verify(session, never()).recordStrongAuthentication(any(), any());
  }

  @Test
  void complete_shouldReturnExpiredWithoutRefreshingSession_whenChallengeTimedOut() {
    when(flowService.resolveUserId(CHALLENGE_REFERENCE)).thenReturn(Optional.of(USER_ID));
    when(flowService.snapshot(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW))
        .thenReturn(terminalSnapshot(AuthenticationOperationStatusEnum.EXPIRED));

    ReauthenticationDecisionVO result = service.complete(
        USER_ID,
        SESSION_REFERENCE,
        CHALLENGE_REFERENCE,
        AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW);

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.EXPIRED);
    verify(contextRepository, never()).findByAuthenticationFlowIdForUpdate(any());
    verify(flowService, never()).consume(any(), any(), any());
    verify(session, never()).recordStrongAuthentication(any(), any());
  }

  @Test
  void complete_shouldReturnConflictWithoutConsuming_whenMethodBecameUnavailable() {
    ReauthenticationContextEntity context = mock(ReauthenticationContextEntity.class);
    when(context.getAuthSession()).thenReturn(session);
    when(context.getOperation()).thenReturn(ReauthenticationOperationEnum.CHANGE_PASSWORD);
    when(contextRepository.findByAuthenticationFlowIdForUpdate(FLOW_ID))
        .thenReturn(Optional.of(context));
    when(flowService.resolveUserId(CHALLENGE_REFERENCE)).thenReturn(Optional.of(USER_ID));
    when(flowService.snapshot(
        CHALLENGE_REFERENCE, AuthenticationFlowPurposeEnum.REAUTHENTICATION, NOW))
        .thenReturn(openSnapshot(List.of()));
    when(availabilityService.availableMethods(USER_ID)).thenReturn(Set.of());

    ReauthenticationDecisionVO result = service.complete(
        USER_ID,
        SESSION_REFERENCE,
        CHALLENGE_REFERENCE,
        AuthenticationMethodEnum.PASSWORD,
        "CorrectPassword1!",
        NOW);

    assertThat(result.status()).isEqualTo(ReauthenticationStatusEnum.CONFLICT);
    verify(flowService, never()).verifyMethod(any(), any(), any(), any(), any(), any());
    verify(flowService, never()).consume(any(), any(), any());
    verify(session, never()).recordStrongAuthentication(any(), any());
  }

  private static AuthenticationFlowSnapshotVO openSnapshot(
      List<AuthenticationFlowVerifiedMethodVO> verifiedMethods) {
    return new AuthenticationFlowSnapshotVO(
        AuthenticationOperationStatusEnum.OPEN,
        FLOW_ID,
        USER_ID,
        AuthenticationFlowPurposeEnum.REAUTHENTICATION,
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD),
        verifiedMethods,
        false,
        NOW.plus(Duration.ofMinutes(5)),
        CORRELATION_ID);
  }

  private static AuthenticationFlowInspectionVO inspection(
      AuthenticationOperationStatusEnum status) {
    return new AuthenticationFlowInspectionVO(
        status,
        USER_ID,
        AuthenticationFlowPurposeEnum.REAUTHENTICATION,
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD),
        false,
        NOW.plus(Duration.ofMinutes(5)),
        CORRELATION_ID);
  }

  private static AuthenticationFlowSnapshotVO terminalSnapshot(
      AuthenticationOperationStatusEnum status) {
    return new AuthenticationFlowSnapshotVO(
        status,
        FLOW_ID,
        USER_ID,
        AuthenticationFlowPurposeEnum.REAUTHENTICATION,
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(AuthenticationMethodEnum.PASSWORD),
        List.of(),
        false,
        NOW,
        CORRELATION_ID);
  }
}
