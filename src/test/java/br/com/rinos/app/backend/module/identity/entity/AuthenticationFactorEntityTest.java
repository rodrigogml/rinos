package br.com.rinos.app.backend.module.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;

@DisplayName("Estados persistentes dos fatores")
class AuthenticationFactorEntityTest {
  private static final Instant NOW = Instant.parse("2026-08-08T15:00:00Z");

  @Test
  void totp_shouldRequireConfirmationAndRejectReplayedStep() {
    TotpFactorEntity factor = new TotpFactorEntity(user(), UUID.randomUUID(), "Celular",
        new byte[] {1, 2}, new byte[12], "key-1", NOW.plusSeconds(300));
    factor.confirm(100, NOW);
    factor.acceptStep(101, NOW.plusSeconds(30));
    assertThat(factor.getStatus()).isEqualTo(TotpFactorStatusEnum.ACTIVE);
    assertThat(factor.getLastAcceptedStep()).isEqualTo(101);
    assertThatThrownBy(() -> factor.acceptStep(101, NOW.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void emailFactor_shouldDisableAndAllowExplicitReactivation() {
    EmailFactorEntity factor = new EmailFactorEntity(user(), UUID.randomUUID(), NOW);
    factor.disable(NOW.plusSeconds(1));
    factor.activate(NOW.plusSeconds(2));
    assertThat(factor.getStatus()).isEqualTo(EmailFactorStatusEnum.ACTIVE);
    assertThat(factor.getDisabledAt()).isNull();
  }

  @Test
  void recoverySet_shouldCloseWhenLastCodeIsConsumed() {
    RecoveryCodeSetEntity set = new RecoveryCodeSetEntity(user(), UUID.randomUUID(), NOW);
    RecoveryCodeEntity code = new RecoveryCodeEntity(set, "{argon2}hash", 1);
    code.use(NOW.plusSeconds(1));
    set.exhaust(NOW.plusSeconds(1));
    assertThat(code.getStatus()).isEqualTo(RecoveryCodeStatusEnum.USED);
    assertThat(set.getStatus()).isEqualTo(RecoveryCodeSetStatusEnum.EXHAUSTED);
    assertThat(set.getActiveMarker()).isNull();
  }

  @Test
  void passkey_shouldDefensivelyCopyMaterialAndKeepRevocationSelective() {
    PasskeyUserEntity owner = new PasskeyUserEntity(user(), new byte[32]);
    byte[] credentialId = new byte[] {1, 2};
    PasskeyCredentialEntity credential = new PasskeyCredentialEntity(owner, UUID.randomUUID(),
        "public-key", credentialId, new byte[] {3}, 0, true, true, false,
        "internal,smart-card,usb", new byte[] {4}, new byte[] {5}, "Notebook");
    credentialId[0] = 9;
    credential.recordUse(1, true, NOW);
    credential.revoke(NOW.plusSeconds(1));
    assertThat(credential.getCredentialId()).containsExactly(1, 2);
    assertThat(credential.getStatus()).isEqualTo(PasskeyCredentialStatusEnum.REVOKED);
    assertThat(credential.isBackupState()).isTrue();
  }

  private static UserEntity user() {
    return new UserEntity("factor@example.test", "factor@example.test", UserStatusEnum.ACTIVE);
  }
}
