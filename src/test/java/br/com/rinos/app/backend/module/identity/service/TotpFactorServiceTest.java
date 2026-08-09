package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.TotpFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpEnrollmentStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.EncryptedAuthenticationSecretVO;
import br.com.rinos.app.backend.module.identity.vo.ProtectedTotpEnrollmentVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.vo.RFWTotpEnrollmentVO;

@DisplayName("Lifecycle transacional do fator TOTP")
class TotpFactorServiceTest {

  private static final long USER_ID = 31L;
  private static final Instant NOW = Instant.parse("2026-08-09T18:00:00Z");
  private UserRepository users;
  private TotpFactorRepository factors;
  private TotpProtocolService protocol;
  private IdentityAuditService audit;
  private IdentityReferenceService references;
  private UserEntity user;
  private TotpFactorService service;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    factors = mock(TotpFactorRepository.class);
    protocol = mock(TotpProtocolService.class);
    audit = mock(IdentityAuditService.class);
    references = new IdentityReferenceService();
    user = new UserEntity("totp@example.test", "totp@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", USER_ID);
    when(users.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
    when(factors.saveAndFlush(any(TotpFactorEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    service = new TotpFactorService(
        users,
        factors,
        mock(AuthenticationMethodInventoryService.class),
        references,
        audit,
        protocol,
        new AuthenticationMfaPropertiesConfig(
            Duration.ofMinutes(5), 2, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)));
  }

  @Test
  void begin_shouldInvalidatePreviousPendingAndReturnOnlyNewPresentation() {
    TotpFactorEntity previous = factor(UUID.randomUUID(), NOW.plusSeconds(60));
    when(factors.findByUserIdAndStatusForUpdate(USER_ID, TotpFactorStatusEnum.PENDING))
        .thenReturn(List.of(previous));
    when(protocol.create(any(Long.class), any(UUID.class), any(String.class)))
        .thenReturn(new ProtectedTotpEnrollmentVO(
            new RFWTotpEnrollmentVO("SECRET", "otpauth://totp/Rinos:user"),
            new EncryptedAuthenticationSecretVO(new byte[17], new byte[12], "v1")));

    var issued = service.begin(USER_ID, NOW);

    assertThat(previous.getStatus()).isEqualTo(TotpFactorStatusEnum.REVOKED);
    assertThat(issued.expiresAt()).isEqualTo(NOW.plusSeconds(300));
    assertThat(issued.manualSecret()).isEqualTo("SECRET");
    ArgumentCaptor<TotpFactorEntity> saved = ArgumentCaptor.forClass(TotpFactorEntity.class);
    verify(factors).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(TotpFactorStatusEnum.PENDING);
    assertThat(saved.getValue().getEnrollmentExpiresAt()).isEqualTo(NOW.plusSeconds(300));
  }

  @Test
  void confirm_shouldActivateAndConsumeMatchedStep() {
    UUID reference = UUID.randomUUID();
    TotpFactorEntity pending = factor(reference, NOW.plusSeconds(300));
    when(factors.findByUserIdAndReferenceForUpdate(USER_ID, references.encode(reference)))
        .thenReturn(Optional.of(pending));
    when(protocol.acceptedStep(any(Long.class), any(UUID.class), any(), any(), any()))
        .thenReturn(OptionalLong.of(42));

    TotpEnrollmentStatusEnum result = service.confirm(
        USER_ID, reference, "123456", UUID.randomUUID(), NOW);

    assertThat(result).isEqualTo(TotpEnrollmentStatusEnum.ACTIVE);
    assertThat(pending.getStatus()).isEqualTo(TotpFactorStatusEnum.ACTIVE);
    assertThat(pending.getLastAcceptedStep()).isEqualTo(42);
    assertThat(pending.getLastUsedAt()).isEqualTo(NOW);
  }

  @Test
  void confirm_shouldExpireOrExhaustWithoutActivating() {
    UUID expiredReference = UUID.randomUUID();
    TotpFactorEntity expired = factor(expiredReference, NOW);
    when(factors.findByUserIdAndReferenceForUpdate(
        USER_ID, references.encode(expiredReference))).thenReturn(Optional.of(expired));

    assertThat(service.confirm(
        USER_ID, expiredReference, "123456", UUID.randomUUID(), NOW))
        .isEqualTo(TotpEnrollmentStatusEnum.EXPIRED);
    assertThat(expired.getStatus()).isEqualTo(TotpFactorStatusEnum.REVOKED);

    UUID rejectedReference = UUID.randomUUID();
    TotpFactorEntity rejected = factor(rejectedReference, NOW.plusSeconds(300));
    when(factors.findByUserIdAndReferenceForUpdate(
        USER_ID, references.encode(rejectedReference))).thenReturn(Optional.of(rejected));
    when(protocol.acceptedStep(any(Long.class), any(UUID.class), any(), any(), any()))
        .thenReturn(OptionalLong.empty());

    assertThat(service.confirm(
        USER_ID, rejectedReference, "000000", UUID.randomUUID(), NOW))
        .isEqualTo(TotpEnrollmentStatusEnum.REJECTED);
    assertThat(service.confirm(
        USER_ID, rejectedReference, "000000", UUID.randomUUID(), NOW.plusSeconds(1)))
        .isEqualTo(TotpEnrollmentStatusEnum.ATTEMPTS_EXHAUSTED);
    assertThat(rejected.getStatus()).isEqualTo(TotpFactorStatusEnum.REVOKED);
  }

  @Test
  void verifyActive_shouldRejectConsumedStepAndAcceptOnlyNewerStep() {
    UUID reference = UUID.randomUUID();
    TotpFactorEntity active = factor(reference, NOW.plusSeconds(300));
    active.confirm(42, NOW.minusSeconds(1));
    when(factors.findByUserIdAndStatusForUpdate(USER_ID, TotpFactorStatusEnum.ACTIVE))
        .thenReturn(List.of(active));
    when(protocol.acceptedStep(any(Long.class), any(UUID.class), any(), any(), any()))
        .thenReturn(OptionalLong.of(42), OptionalLong.of(43));

    assertThat(service.verifyActive(USER_ID, "123456", NOW))
        .isEqualTo(FactorOperationStatusEnum.REJECTED);
    assertThat(service.verifyActive(USER_ID, "654321", NOW.plusSeconds(30)))
        .isEqualTo(FactorOperationStatusEnum.USED);
    assertThat(active.getLastAcceptedStep()).isEqualTo(43);
  }

  private TotpFactorEntity factor(UUID reference, Instant expiresAt) {
    return new TotpFactorEntity(
        user, reference, "Aplicativo autenticador", new byte[17], new byte[12], "v1", expiresAt);
  }
}
