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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.PasswordRecoveryEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginOperationEnum;
import br.com.rinos.app.backend.module.identity.enums.OriginReservationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasswordRecoveryStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasswordRecoveryRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;
import br.com.rinos.app.backend.module.identity.vo.OriginAddressVO;
import br.com.rinos.app.backend.module.identity.vo.OriginReservationResultVO;
import br.com.rinos.app.backend.module.identity.vo.PasswordRecoveryOperationVO;
import br.com.rinos.app.config.PasswordRecoveryPropertiesConfig;

@DisplayName("Recuperação transacional de senha")
class PasswordRecoveryServiceTest {

  private UserRepository users;
  private LocalCredentialRepository credentials;
  private PasswordRecoveryRepository recoveries;
  private EmailNormalizationService emails;
  private OriginAddressService origins;
  private OriginLimitService limits;
  private VerificationTokenService tokens;
  private LocalCredentialService credentialService;
  private IdentityAuditService audit;
  private VerificationEmailDispatchService dispatch;
  private PasswordRecoveryService service;
  private OriginAddressVO origin;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    credentials = mock(LocalCredentialRepository.class);
    recoveries = mock(PasswordRecoveryRepository.class);
    emails = mock(EmailNormalizationService.class);
    origins = mock(OriginAddressService.class);
    limits = mock(OriginLimitService.class);
    tokens = mock(VerificationTokenService.class);
    credentialService = mock(LocalCredentialService.class);
    audit = mock(IdentityAuditService.class);
    dispatch = mock(VerificationEmailDispatchService.class);
    PublicApplicationUriService publicUris = mock(PublicApplicationUriService.class);
    origin = new OriginAddressVO(new byte[] {(byte) 203, 0, 113, 10});
    when(origins.normalize("203.0.113.10")).thenReturn(origin);
    when(limits.reserve(any(), any(), any(Integer.class), any(Duration.class)))
        .thenReturn(new OriginReservationResultVO(
            OriginReservationStatusEnum.RESERVED, null));
    when(publicUris.passwordResetUri(any())).thenReturn(
        java.net.URI.create("https://app.rinos.com.br/login?step=password-reset&proof=redacted"));
    when(dispatch.scheduleAfterCommit(any())).thenReturn(
        CompletableFuture.completedFuture(mock(
            br.com.rinos.app.backend.module.identity.vo.VerificationEmailDispatchResultVO.class)));
    service = new PasswordRecoveryService(
        users,
        credentials,
        recoveries,
        emails,
        origins,
        limits,
        tokens,
        credentialService,
        audit,
        dispatch,
        publicUris,
        new PasswordRecoveryPropertiesConfig(
            Duration.ofHours(1),
            3,
            Duration.ofMinutes(15),
            20,
            Duration.ofMinutes(15),
            Duration.ofDays(30)));
  }

  @Test
  void issue_shouldReturnSameNeutralResult_whenEmailDoesNotExist() {
    when(emails.normalize("unknown@example.test")).thenReturn(
        new NormalizedEmailVO("unknown@example.test", "unknown@example.test"));
    when(users.findByNormalizedEmailForUpdate("unknown@example.test"))
        .thenReturn(Optional.empty());

    PasswordRecoveryOperationVO result = service.issue(
        "unknown@example.test",
        "203.0.113.10",
        Locale.forLanguageTag("pt-BR"),
        UUID.randomUUID(),
        Instant.parse("2026-08-02T12:00:00Z"));

    assertThat(result.status()).isEqualTo(PasswordRecoveryOperationStatusEnum.ACCEPTED);
    assertThat(result.dispatch()).isNull();
    verify(recoveries, never()).saveAndFlush(any());
  }

  @Test
  void issue_shouldInvalidatePreviousProofAndDispatchOnlyForActiveLocalUser() {
    Instant now = Instant.parse("2026-08-02T12:00:00Z");
    UserEntity user = activeUser();
    PasswordRecoveryEntity previous = new PasswordRecoveryEntity(
        user, new byte[32], now.minusSeconds(60), now.plusSeconds(60));
    when(emails.normalize("person@example.test")).thenReturn(
        new NormalizedEmailVO("person@example.test", "person@example.test"));
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(credentials.findByUserIdAndStatus(1L, LocalCredentialStatusEnum.ACTIVE))
        .thenReturn(Optional.of(new LocalCredentialEntity(user, "{argon2}hash")));
    when(recoveries.countByUserIdAndIssuedAtGreaterThanEqual(eq(1L), any()))
        .thenReturn(0L);
    when(recoveries.findByUserIdAndStatusForUpdate(
        1L, PasswordRecoveryStatusEnum.OPEN)).thenReturn(List.of(previous));
    when(tokens.generate()).thenReturn("opaque-proof");
    when(tokens.hash("opaque-proof")).thenReturn(new byte[32]);

    PasswordRecoveryOperationVO result = service.issue(
        "person@example.test",
        "203.0.113.10",
        Locale.forLanguageTag("pt-BR"),
        UUID.randomUUID(),
        now);

    assertThat(result.status()).isEqualTo(PasswordRecoveryOperationStatusEnum.ACCEPTED);
    assertThat(result.dispatch()).isNotNull();
    assertThat(previous.getStatus()).isEqualTo(PasswordRecoveryStatusEnum.INVALIDATED);
    ArgumentCaptor<PasswordRecoveryEntity> persisted =
        ArgumentCaptor.forClass(PasswordRecoveryEntity.class);
    verify(recoveries).saveAndFlush(persisted.capture());
    assertThat(persisted.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(1)));
    verify(dispatch).scheduleAfterCommit(any());
    verify(audit).record(eq(user), eq(null), any(), any(), eq(null), eq(null), any(), any(), eq(now));
  }

  @Test
  void reset_shouldReplaceCredentialConsumeProofAndRejectReplay() {
    Instant now = Instant.parse("2026-08-02T12:00:00Z");
    UserEntity user = activeUser();
    PasswordRecoveryEntity recovery = new PasswordRecoveryEntity(
        user, new byte[32], now.minusSeconds(60), now.plusSeconds(60));
    ReflectionTestUtils.setField(recovery, "id", 9L);
    when(tokens.hash("opaque-proof")).thenReturn(new byte[32]);
    when(tokens.matches("opaque-proof", new byte[32])).thenReturn(true);
    when(recoveries.findByTokenHashForUpdate(any())).thenReturn(Optional.of(recovery));
    when(recoveries.findByUserIdAndStatusForUpdate(
        1L, PasswordRecoveryStatusEnum.OPEN)).thenReturn(List.of(recovery));

    PasswordRecoveryOperationVO completed = service.reset(
        "opaque-proof",
        "{argon2}new-hash",
        "203.0.113.10",
        UUID.randomUUID(),
        now);

    assertThat(completed.status()).isEqualTo(PasswordRecoveryOperationStatusEnum.COMPLETED);
    assertThat(recovery.getStatus()).isEqualTo(PasswordRecoveryStatusEnum.USED);
    assertThat(recovery.getUsedAt()).isEqualTo(now);
    verify(credentialService).replaceAndInvalidateSessions(
        eq(user),
        eq("{argon2}new-hash"),
        eq(now),
        any());

    PasswordRecoveryOperationVO replay = service.reset(
        "opaque-proof",
        "{argon2}another-hash",
        "203.0.113.10",
        UUID.randomUUID(),
        now.plusSeconds(1));

    assertThat(replay.status()).isEqualTo(
        PasswordRecoveryOperationStatusEnum.INVALID_PROOF);
  }

  @Test
  void reset_shouldExpireProofWithoutChangingCredential() {
    Instant now = Instant.parse("2026-08-02T12:00:00Z");
    PasswordRecoveryEntity recovery = new PasswordRecoveryEntity(
        activeUser(), new byte[32], now.minusSeconds(120), now.minusSeconds(1));
    when(tokens.hash("expired-proof")).thenReturn(new byte[32]);
    when(tokens.matches("expired-proof", new byte[32])).thenReturn(true);
    when(recoveries.findByTokenHashForUpdate(any())).thenReturn(Optional.of(recovery));

    PasswordRecoveryOperationVO result = service.reset(
        "expired-proof",
        "{argon2}new-hash",
        "203.0.113.10",
        UUID.randomUUID(),
        now);

    assertThat(result.status()).isEqualTo(
        PasswordRecoveryOperationStatusEnum.EXPIRED_PROOF);
    assertThat(recovery.getStatus()).isEqualTo(PasswordRecoveryStatusEnum.EXPIRED);
    verify(credentialService, never()).replace(any(), any());
  }

  private static UserEntity activeUser() {
    UserEntity user = new UserEntity(
        "person@example.test",
        "person@example.test",
        UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
  }
}
