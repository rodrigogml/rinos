package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.RecoveryCodeSetEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.EmailFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.PasskeyCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeSetStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.RecoveryCodeStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.EmailFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.PasskeyCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeRepository;
import br.com.rinos.app.backend.module.identity.repository.RecoveryCodeSetRepository;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;

@DisplayName("Disponibilidade atual dos métodos de autenticação")
class AuthenticationMethodAvailabilityServiceTest {

  private static final Long USER_ID = 41L;

  @Test
  void availableMethods_shouldRequireUsableCurrentStateFromEverySource() {
    Repositories repositories = new Repositories();
    RecoveryCodeSetEntity recoverySet = recoverySet();
    when(repositories.local.existsByUserIdAndStatusAndCompromisedAtIsNull(
        USER_ID, LocalCredentialStatusEnum.ACTIVE)).thenReturn(true);
    when(repositories.external.existsByUserIdAndProviderAndStatus(
        USER_ID, ExternalIdentityProviderEnum.GOOGLE, ExternalIdentityStatusEnum.ACTIVE))
        .thenReturn(true);
    when(repositories.passkey.countByPasskeyUserUserIdAndStatus(
        USER_ID, PasskeyCredentialStatusEnum.ACTIVE)).thenReturn(1L);
    when(repositories.totp.countByUserIdAndStatus(USER_ID, TotpFactorStatusEnum.ACTIVE))
        .thenReturn(1L);
    when(repositories.email.existsByUserIdAndStatus(USER_ID, EmailFactorStatusEnum.ACTIVE))
        .thenReturn(true);
    when(repositories.recoverySet.findByUserIdAndStatus(
        USER_ID, RecoveryCodeSetStatusEnum.ACTIVE)).thenReturn(Optional.of(recoverySet));
    when(repositories.recoveryCode.countByCodeSetIdAndStatus(
        77L, RecoveryCodeStatusEnum.AVAILABLE)).thenReturn(1L);

    assertThat(repositories.service().availableMethods(USER_ID))
        .containsExactlyInAnyOrder(AuthenticationMethodEnum.values());
  }

  @Test
  void availableMethods_shouldOmitCompromisedAndExhaustedMethods() {
    Repositories repositories = new Repositories();
    RecoveryCodeSetEntity recoverySet = recoverySet();
    when(repositories.recoverySet.findByUserIdAndStatus(
        USER_ID, RecoveryCodeSetStatusEnum.ACTIVE)).thenReturn(Optional.of(recoverySet));
    when(repositories.recoveryCode.countByCodeSetIdAndStatus(
        77L, RecoveryCodeStatusEnum.AVAILABLE)).thenReturn(0L);

    assertThat(repositories.service().availableMethods(USER_ID)).isEmpty();
  }

  private static RecoveryCodeSetEntity recoverySet() {
    UserEntity user = new UserEntity(
        "person@example.test", "person@example.test", UserStatusEnum.ACTIVE);
    RecoveryCodeSetEntity recoverySet = new RecoveryCodeSetEntity(
        user, UUID.randomUUID(), Instant.parse("2026-08-09T12:00:00Z"));
    ReflectionTestUtils.setField(recoverySet, "id", 77L);
    return recoverySet;
  }

  private static final class Repositories {

    private final LocalCredentialRepository local = mock(LocalCredentialRepository.class);
    private final ExternalIdentityRepository external = mock(ExternalIdentityRepository.class);
    private final PasskeyCredentialRepository passkey = mock(PasskeyCredentialRepository.class);
    private final TotpFactorRepository totp = mock(TotpFactorRepository.class);
    private final EmailFactorRepository email = mock(EmailFactorRepository.class);
    private final RecoveryCodeSetRepository recoverySet = mock(RecoveryCodeSetRepository.class);
    private final RecoveryCodeRepository recoveryCode = mock(RecoveryCodeRepository.class);

    private AuthenticationMethodAvailabilityService service() {
      return new AuthenticationMethodAvailabilityService(
          local, external, passkey, totp, email, recoverySet, recoveryCode);
    }
  }
}
