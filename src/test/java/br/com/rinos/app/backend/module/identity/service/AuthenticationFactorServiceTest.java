package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        mock(PasskeyUserRepository.class), credentials, inventory, references, audit);

    FactorOperationStatusEnum result = service.revoke(11L, credential.getReference(),
        false, UUID.randomUUID(), NOW);

    assertThat(result).isEqualTo(FactorOperationStatusEnum.LAST_METHOD);
    assertThat(credential.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.ACTIVE);
    verify(audit, never()).record(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void passkeyRevoke_shouldProtectLastAdministrativeFactor() {
    PasskeyCredentialRepository credentials = mock(PasskeyCredentialRepository.class);
    PasskeyCredentialEntity credential = credential();
    when(credentials.findByUserIdAndReferenceForUpdate(any(), any())).thenReturn(Optional.of(credential));
    AuthenticationMethodInventoryService inventory = mock(AuthenticationMethodInventoryService.class);
    when(inventory.inspect(11L)).thenReturn(new AuthenticationMethodInventoryVO(true, false, 1, 0, false, 1));
    PasskeyCredentialService service = new PasskeyCredentialService(users,
        mock(PasskeyUserRepository.class), credentials, inventory, references, audit);

    FactorOperationStatusEnum result = service.revoke(11L, credential.getReference(),
        true, UUID.randomUUID(), NOW);

    assertThat(result).isEqualTo(FactorOperationStatusEnum.ADMIN_FACTOR_REQUIRED);
    assertThat(credential.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.ACTIVE);
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
            java.time.Duration.ofMinutes(15)));

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
    RecoveryCodeService service = new RecoveryCodeService(users, sets, codes, references);

    FactorOperationStatusEnum result = service.consume(11L, "first-hash"::equals, NOW.plusSeconds(1));

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
