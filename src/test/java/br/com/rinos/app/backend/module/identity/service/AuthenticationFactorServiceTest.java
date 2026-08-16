package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import br.com.rinos.app.backend.module.identity.entity.PasskeyCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeEntity;
import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.entity.TotpFactorEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.FactorOperationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;
import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeSetRepository;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationMethodInventoryVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.service.RFWRecoveryCodeService;

@DisplayName("Invariantes transacionais dos fatores")
class AuthenticationFactorServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-08T15:00:00Z");
  private UserRepository users;
  private UserEntity user;
  private IdentityReferenceService references;
  private IdentityAuditService audit;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class); references = new IdentityReferenceService();
    audit = mock(IdentityAuditService.class);
    user = new UserEntity("factor@example.test", "factor@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 11L);
    when(users.findByIdForUpdate(11L)).thenReturn(Optional.of(user));
  }

  @Test
  void passkeyRevoke_shouldProtectLastInitialMethod() {
    PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    PasskeyCredentialEntity credential = credential();
    when(credentials.findByUserIdAndReferenceForUpdate(any(), any())).thenReturn(Optional.of(credential));
    AuthenticationMethodInventoryService inventory = mock(AuthenticationMethodInventoryService.class);
    when(inventory.inspect(11L)).thenReturn(new AuthenticationMethodInventoryVO(false, false, 1, 0, false, 1));
    PasskeyCredentialService service = new PasskeyCredentialService(users,
        mock(PasskeyUserRepository.class), credentials, inventory, references, audit,
        mock(AdministrativeFactorContinuityPort.class));

    FactorOperationStatusEnum result = service.revoke(11L, credential.getReference(),
        false, UUID.randomUUID(), NOW);

    assertThat(result).isEqualTo(FactorOperationStatusEnum.LAST_METHOD);
    assertThat(credential.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.ACTIVE);
    verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void passkeyRevoke_shouldDelegateLastAdministrativeFactorToEffectiveContinuity() {
    PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    PasskeyCredentialEntity credential = credential();
    when(credentials.findByUserIdAndReferenceForUpdate(any(), any())).thenReturn(Optional.of(credential));
    AuthenticationMethodInventoryService inventory = mock(AuthenticationMethodInventoryService.class);
    when(inventory.inspect(11L)).thenReturn(new AuthenticationMethodInventoryVO(true, false, 1, 0, false, 1));
    AdministrativeFactorContinuityPort continuity = mock(AdministrativeFactorContinuityPort.class);
    AdministrativeFactorContinuityContext context =
        new AdministrativeFactorContinuityContext(11L, List.of(42L));
    when(continuity.lockContexts(11L)).thenReturn(context);
    doThrow(new IllegalArgumentException("administrative continuity would be lost"))
        .when(continuity).validateAndRevise(context, NOW);
    PasskeyCredentialService service = new PasskeyCredentialService(users,
        mock(PasskeyUserRepository.class), credentials, inventory, references, audit,
        continuity);

    assertThatThrownBy(() -> service.revoke(11L, credential.getReference(),
        true, UUID.randomUUID(), NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("continuity would be lost");
    verify(credentials).flush();
  }

  @Test
  void passkeyRevoke_shouldRevokeOnlySelectedCredential_whenAnotherMethodRemains() {
    PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    PasskeyCredentialEntity credential = credential();
    when(credentials.findByUserIdAndReferenceForUpdate(any(), any()))
        .thenReturn(Optional.of(credential));
    AuthenticationMethodInventoryService inventory = mock(AuthenticationMethodInventoryService.class);
    when(inventory.inspect(11L))
        .thenReturn(new AuthenticationMethodInventoryVO(true, false, 2, 0, false, 2));
    PasskeyCredentialService service = new PasskeyCredentialService(
        users,
        mock(PasskeyUserRepository.class),
        credentials,
        inventory,
        references,
        audit,
        mock(AdministrativeFactorContinuityPort.class));
    UUID correlationId = UUID.randomUUID();

    FactorOperationStatusEnum result = service.revoke(
        11L, credential.getReference(), false, correlationId, NOW);

    assertThat(result).isEqualTo(FactorOperationStatusEnum.REVOKED);
    assertThat(credential.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.REVOKED);
    verify(audit).record(
        user,
        null,
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_METHOD_REMOVED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "PASSKEY",
        NOW);
  }

  @Test
  void passkeyRename_shouldChangeOnlyLabelAndAuditOwnedActiveCredential() {
    PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    PasskeyCredentialEntity credential = credential();
    when(credentials.findByUserIdAndReferenceForUpdate(any(), any()))
        .thenReturn(Optional.of(credential));
    PasskeyCredentialService service = new PasskeyCredentialService(
        users,
        mock(PasskeyUserRepository.class),
        credentials,
        mock(AuthenticationMethodInventoryService.class),
        references,
        audit,
        mock(AdministrativeFactorContinuityPort.class));
    UUID correlationId = UUID.randomUUID();

    service.rename(11L, credential.getReference(), "Chave principal", correlationId, NOW);

    assertThat(credential.getLabel()).isEqualTo("Chave principal");
    assertThat(credential.getPublicKey()).containsExactly((byte) 2);
    verify(audit).record(
        user,
        null,
        correlationId,
        IdentityEventTypeEnum.AUTHENTICATION_METHOD_RENAMED,
        null,
        null,
        IdentityTransitionOriginEnum.SELF_SERVICE,
        "PASSKEY",
        NOW);
  }

  @Test
  void totpRevoke_shouldAllowSelectiveRemovalWhenAnotherAdministrativeFactorRemains() {
    TotpFactorRepository factors = mock(TotpFactorRepository.class);
    TotpFactorEntity factor = new TotpFactorEntity(user, UUID.randomUUID(), "Celular",
        new byte[] {1}, new byte[12], "key-1", NOW.plusSeconds(300));
    factor.confirm(1, NOW);
    when(factors.findByUserIdAndReferenceForUpdate(any(), any())).thenReturn(Optional.of(factor));
    AuthenticationMethodInventoryService inventory = mock(AuthenticationMethodInventoryService.class);
    when(inventory.inspect(11L)).thenReturn(new AuthenticationMethodInventoryVO(true, false, 1, 1, false, 1));
    TotpFactorService service = new TotpFactorService(
        users,
        factors,
        inventory,
        references,
        audit,
        mock(TotpProtocolService.class),
        new AuthenticationMfaPropertiesConfig(
            java.time.Duration.ofMinutes(5), 5, java.time.Duration.ofMinutes(1), 3,
            java.time.Duration.ofMinutes(15)),
        mock(AdministrativeFactorContinuityPort.class));

    FactorOperationStatusEnum result = service.revoke(11L, factor.getReference(),
        true, UUID.randomUUID(), NOW.plusSeconds(1));

    assertThat(result).isEqualTo(FactorOperationStatusEnum.REVOKED);
  }

  @Test
  void recoveryConsume_shouldUseOnlyMatchedCodeAndExhaustAtLastOne() {
    RecoveryCodeSetRepository sets = mock(RecoveryCodeSetRepository.class);
    RecoveryCodeRepository codes = mock(RecoveryCodeRepository.class);
    RecoveryCodeSetEntity set = new RecoveryCodeSetEntity(user, UUID.randomUUID(), NOW);
    ReflectionTestUtils.setField(set, "id", 21L);
    RecoveryCodeEntity first = new RecoveryCodeEntity(set, "first-hash", 1);
    RecoveryCodeEntity second = new RecoveryCodeEntity(set, "second-hash", 2);
    second.invalidate();
    when(sets.findByUserIdAndStatusForUpdate(11L, RecoveryCodeSetStatusEnum.ACTIVE))
        .thenReturn(Optional.of(set));
    when(codes.findByCodeSetIdForUpdate(21L)).thenReturn(List.of(first, second));
    RFWRecoveryCodeService protocol = mock(RFWRecoveryCodeService.class);
    when(protocol.findMatchingIndex("first-code", List.of("first-hash"))).thenReturn(0);
    RecoveryCodeService service = new RecoveryCodeService(
        users, sets, codes, references, audit, protocol);

    FactorOperationStatusEnum result = service.consume(11L, "first-code", NOW.plusSeconds(1));

    assertThat(result).isEqualTo(FactorOperationStatusEnum.EXHAUSTED);
    assertThat(first.getStatus()).isEqualTo(RecoveryCodeStatusEnum.USED);
    assertThat(second.getStatus()).isEqualTo(RecoveryCodeStatusEnum.INVALIDATED);
    assertThat(set.getStatus()).isEqualTo(RecoveryCodeSetStatusEnum.EXHAUSTED);
  }

  private PasskeyCredentialEntity credential() {
    PasskeyUserEntity owner = new PasskeyUserEntity(user, new byte[32]);
    return new PasskeyCredentialEntity(owner, UUID.randomUUID(), "public-key",
        new byte[] {1}, new byte[] {2}, 0, true, false, false, "internal",
        new byte[] {3}, new byte[] {4}, "Notebook");
  }
}
