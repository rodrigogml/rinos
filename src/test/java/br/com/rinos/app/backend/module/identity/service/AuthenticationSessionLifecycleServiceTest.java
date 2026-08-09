package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthenticationFlowEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationSessionLifecycleStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthenticationFlowRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowInspectionVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowSnapshotVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationFlowVerifiedMethodVO;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationSessionLifecycleVO;
import br.com.rinos.app.backend.module.identity.vo.LegalRequirementStatusVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import br.com.rinos.app.config.AuthenticationRetentionPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWOpaqueTokenService;

@DisplayName("Lifecycle transacional da sessão global")
class AuthenticationSessionLifecycleServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("a8dd088c-d079-4b7b-8c10-4f81cfbc899a");
  private static final String FLOW_REFERENCE = "opaque-flow-reference";

  private AuthSessionRepository sessionRepository;
  private AuthSessionMethodRepository methodRepository;
  private AuthenticationFlowRepository flowRepository;
  private UserRepository userRepository;
  private AuthenticationFlowService flowService;
  private LegalConsentService legalConsentService;
  private IdentityAuditService auditService;
  private AuthenticationSessionLifecycleService service;
  private UserEntity user;
  private AuthenticationFlowEntity flow;
  private AuthSessionEntity persistedSession;

  @BeforeEach
  void setUp() {
    sessionRepository = mock(AuthSessionRepository.class);
    methodRepository = mock(AuthSessionMethodRepository.class);
    flowRepository = mock(AuthenticationFlowRepository.class);
    userRepository = mock(UserRepository.class);
    flowService = mock(AuthenticationFlowService.class);
    legalConsentService = mock(LegalConsentService.class);
    auditService = mock(IdentityAuditService.class);
    user = new UserEntity("person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
    flow = new AuthenticationFlowEntity(
        user,
        new byte[32],
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        false,
        NOW.minusSeconds(10),
        NOW.plusSeconds(300),
        CORRELATION_ID);
    ReflectionTestUtils.setField(flow, "id", 91L);
    when(flowService.resolveUserId(FLOW_REFERENCE)).thenReturn(Optional.of(41L));
    when(userRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(user));
    when(flowService.snapshot(FLOW_REFERENCE, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(AuthenticationOperationStatusEnum.OPEN));
    when(flowService.snapshotById(91L, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(snapshot(AuthenticationOperationStatusEnum.OPEN));
    when(flowService.consumeById(91L, AuthenticationFlowPurposeEnum.SIGN_IN, NOW))
        .thenReturn(flowResult(AuthenticationOperationStatusEnum.USED));
    when(legalConsentService.evaluateRequiredConsents(41L, NOW))
        .thenReturn(new LegalRequirementStatusVO(List.of(1L, 2L), List.of()));
    when(flowRepository.getReferenceById(91L)).thenReturn(flow);
    when(sessionRepository.findByAuthenticationFlowIdForUpdate(91L))
        .thenReturn(Optional.empty());
    when(sessionRepository.saveAndFlush(any(AuthSessionEntity.class))).thenAnswer(invocation -> {
      persistedSession = invocation.getArgument(0);
      return persistedSession;
    });
    service = new AuthenticationSessionLifecycleService(
        sessionRepository,
        methodRepository,
        flowRepository,
        userRepository,
        flowService,
        new AuthenticationAssurancePolicyService(),
        legalConsentService,
        new RFWOpaqueTokenService(),
        new IdentityReferenceService(),
        auditService,
        sessionProperties());
  }

  @Test
  void prepare_shouldPersistUnusableSessionWithoutSuccessAudit() {
    AuthenticationSessionLifecycleVO result = prepare();

    assertThat(result.status()).isEqualTo(AuthenticationSessionLifecycleStatusEnum.PREPARED);
    assertThat(result.userId()).isEqualTo(41L);
    assertThat(result.sessionReference()).isNotNull();
    assertThat(persistedSession.getStatus()).isEqualTo(AuthSessionStatusEnum.PREPARED);
    assertThat(persistedSession.getActivatedAt()).isNull();
    verify(methodRepository).save(any());
    verify(auditService, never()).record(
        any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void publish_shouldConsumeFlowActivateSessionAndAuditOnce() {
    AuthenticationSessionLifecycleVO prepared = prepare();
    stubSessionLookup(prepared.sessionReference());

    AuthenticationSessionLifecycleVO result = service.publish(prepared.sessionReference(), NOW);

    assertThat(result.status()).isEqualTo(AuthenticationSessionLifecycleStatusEnum.ACTIVE);
    assertThat(persistedSession.getStatus()).isEqualTo(AuthSessionStatusEnum.ACTIVE);
    assertThat(persistedSession.getActivatedAt()).isEqualTo(NOW);
    verify(flowService).consumeById(91L, AuthenticationFlowPurposeEnum.SIGN_IN, NOW);
    verify(auditService, times(2)).record(
        any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void prepareAndPublish_shouldBeIdempotent() {
    AuthenticationSessionLifecycleVO firstPreparation = prepare();
    when(sessionRepository.findByAuthenticationFlowIdForUpdate(91L))
        .thenReturn(Optional.of(persistedSession));

    AuthenticationSessionLifecycleVO repeatedPreparation = prepare();
    stubSessionLookup(firstPreparation.sessionReference());
    AuthenticationSessionLifecycleVO firstPublication = service.publish(
        firstPreparation.sessionReference(), NOW);
    AuthenticationSessionLifecycleVO repeatedPublication = service.publish(
        firstPreparation.sessionReference(), NOW);

    assertThat(repeatedPreparation.sessionReference())
        .isEqualTo(firstPreparation.sessionReference());
    assertThat(firstPublication.status()).isEqualTo(AuthenticationSessionLifecycleStatusEnum.ACTIVE);
    assertThat(repeatedPublication.status()).isEqualTo(AuthenticationSessionLifecycleStatusEnum.ACTIVE);
    verify(flowService, times(1)).consumeById(
        91L, AuthenticationFlowPurposeEnum.SIGN_IN, NOW);
    verify(auditService, times(2)).record(
        any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void abort_shouldRevokePreparationAndReleaseFlowWithoutAudit() {
    AuthenticationSessionLifecycleVO prepared = prepare();
    stubSessionLookup(prepared.sessionReference());

    service.abort(prepared.sessionReference(), NOW);

    assertThat(persistedSession.getStatus()).isEqualTo(AuthSessionStatusEnum.REVOKED);
    assertThat(persistedSession.getAuthenticationFlow()).isNull();
    verify(auditService, never()).record(
        any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void cleanup_shouldExpireAbandonedPreparationWithoutSuccessOrSessionAudit() {
    prepare();
    Instant cleanupAt = NOW.plus(Duration.ofHours(12));
    when(sessionRepository.findExpiredByStatusForUpdate(
        AuthSessionStatusEnum.ACTIVE, cleanupAt)).thenReturn(List.of());
    when(sessionRepository.findExpiredByStatusForUpdate(
        AuthSessionStatusEnum.PREPARED, cleanupAt)).thenReturn(List.of(persistedSession));
    AuthSessionService cleanup = new AuthSessionService(
        sessionRepository,
        methodRepository,
        userRepository,
        new RFWOpaqueTokenService(),
        new IdentityReferenceService(),
        auditService,
        sessionProperties(),
        new AuthenticationRetentionPropertiesConfig(
            Duration.ofDays(30), Duration.ofDays(30), Duration.ofDays(365)));

    assertThat(cleanup.cleanup(cleanupAt).expired()).isEqualTo(1);

    assertThat(persistedSession.getStatus()).isEqualTo(AuthSessionStatusEnum.EXPIRED);
    verify(auditService, never()).record(
        any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  private AuthenticationSessionLifecycleVO prepare() {
    return service.prepare(
        FLOW_REFERENCE,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        41L,
        false,
        new byte[] {127, 0, 0, 1},
        "Browser/1.0",
        NOW);
  }

  private void stubSessionLookup(UUID reference) {
    when(sessionRepository.findByPublicReference(any())).thenReturn(Optional.of(persistedSession));
    when(sessionRepository.findByPublicReferenceForUpdate(any()))
        .thenReturn(Optional.of(persistedSession));
  }

  private static AuthenticationFlowSnapshotVO snapshot(
      AuthenticationOperationStatusEnum status) {
    return new AuthenticationFlowSnapshotVO(
        status,
        91L,
        41L,
        AuthenticationFlowPurposeEnum.SIGN_IN,
        AuthenticationMethodEnum.PASSWORD,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(new AuthenticationFlowVerifiedMethodVO(
            AuthenticationMethodEnum.PASSWORD, NOW.minusSeconds(1), null)),
        false,
        NOW.plusSeconds(300),
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
        NOW.plusSeconds(300),
        CORRELATION_ID);
  }

  private static AuthenticationSessionPropertiesConfig sessionProperties() {
    return new AuthenticationSessionPropertiesConfig(
        Duration.ofHours(12),
        Duration.ofMinutes(30),
        Duration.ofDays(30),
        Duration.ofDays(7),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        "RINOS_AUTH",
        true,
        "Strict");
  }
}
