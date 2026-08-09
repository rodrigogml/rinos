package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.backend.module.identity.vo.NormalizedEmailVO;

@DisplayName("Verificação da credencial local para autenticação")
class PasswordCredentialAuthenticationServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private EmailNormalizationService normalization;
  private UserRepository users;
  private LocalCredentialRepository credentials;
  private PasswordEncoder encoder;
  private PasswordCredentialAuthenticationService service;

  @BeforeEach
  void setUp() {
    normalization = mock(EmailNormalizationService.class);
    users = mock(UserRepository.class);
    credentials = mock(LocalCredentialRepository.class);
    encoder = mock(PasswordEncoder.class);
    when(encoder.encode(any(CharSequence.class))).thenReturn("{argon2id}sentinel");
    service = new PasswordCredentialAuthenticationService(
        normalization, users, credentials, encoder);
    clearInvocations(encoder);
    when(normalization.normalize("Person@Example.test"))
        .thenReturn(new NormalizedEmailVO("Person@Example.test", "person@example.test"));
  }

  @Test
  void verify_shouldUseSentinelHashAndErasePassword_whenIdentityDoesNotExist() {
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.empty());
    when(credentials.findByUserIdForUpdate(0L)).thenReturn(Optional.empty());
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}sentinel")))
        .thenReturn(false);
    char[] password = "WrongPassword1!".toCharArray();

    OptionalLong result = service.verify("Person@Example.test", password, NOW);

    assertThat(result).isEmpty();
    assertThat(password).containsOnly('\0');
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}sentinel"));
    verify(credentials).findByUserIdForUpdate(0L);
  }

  @Test
  void verify_shouldReturnActiveOwnerAfterOnePasswordComparison() {
    UserEntity user = user(UserStatusEnum.ACTIVE);
    LocalCredentialEntity credential = new LocalCredentialEntity(
        user, "{argon2id}real-hash", NOW.minusSeconds(60));
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(credentials.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}real-hash")))
        .thenReturn(true);
    char[] password = "CorrectPassword1!".toCharArray();

    OptionalLong result = service.verify("Person@Example.test", password, NOW);

    assertThat(result).hasValue(41L);
    assertThat(password).containsOnly('\0');
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}real-hash"));
  }

  @Test
  void verify_shouldCompareRealHashButRejectBlockedOwner() {
    UserEntity user = user(UserStatusEnum.BLOCKED);
    LocalCredentialEntity credential = new LocalCredentialEntity(
        user, "{argon2id}real-hash", NOW.minusSeconds(60));
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(credentials.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}real-hash")))
        .thenReturn(true);

    OptionalLong result = service.verify(
        "Person@Example.test", "CorrectPassword1!".toCharArray(), NOW);

    assertThat(result).isEmpty();
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}real-hash"));
  }

  @Test
  void verify_shouldCompareRealHashButRejectCompromisedCredentialUntilReplacement() {
    UserEntity user = user(UserStatusEnum.ACTIVE);
    LocalCredentialEntity credential = new LocalCredentialEntity(
        user, "{argon2id}real-hash", NOW.minusSeconds(60));
    credential.setCompromisedAt(NOW.minusSeconds(30));
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(credentials.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}real-hash")))
        .thenReturn(true);
    char[] password = "CorrectPassword1!".toCharArray();

    OptionalLong result = service.verify("Person@Example.test", password, NOW);

    assertThat(result).isEmpty();
    assertThat(password).containsOnly('\0');
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}real-hash"));
    verify(encoder, never()).upgradeEncoding(any());
    verify(credentials, never()).save(any());
  }

  @Test
  void verify_shouldStillPaySentinelCostForMalformedIdentifier() {
    when(normalization.normalize("invalid"))
        .thenThrow(new IllegalArgumentException("invalid email"));
    when(users.findByNormalizedEmailForUpdate("__rinos_timing_sentinel__"))
        .thenReturn(Optional.empty());
    when(credentials.findByUserIdForUpdate(0L)).thenReturn(Optional.empty());
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}sentinel")))
        .thenReturn(false);

    OptionalLong result = service.verify("invalid", "Password1!".toCharArray(), NOW);

    assertThat(result).isEmpty();
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}sentinel"));
    verify(users).findByNormalizedEmailForUpdate("__rinos_timing_sentinel__");
    verify(credentials).findByUserIdForUpdate(0L);
  }

  @Test
  void verify_shouldUpgradeOutdatedHashWithoutChangingPasswordDate() {
    UserEntity user = user(UserStatusEnum.ACTIVE);
    Instant passwordChangedAt = NOW.minusSeconds(86_400);
    LocalCredentialEntity credential = new LocalCredentialEntity(
        user, "{argon2id}outdated-hash", passwordChangedAt);
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(credentials.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}outdated-hash")))
        .thenReturn(true);
    when(encoder.upgradeEncoding("{argon2id}outdated-hash")).thenReturn(true);
    when(encoder.encode(any(CharSequence.class))).thenReturn("{argon2id}current-hash");

    OptionalLong result = service.verify(
        "Person@Example.test", "CorrectPassword1!".toCharArray(), NOW);

    assertThat(result).hasValue(41L);
    assertThat(credential.getPasswordHash()).isEqualTo("{argon2id}current-hash");
    assertThat(credential.getPasswordChangedAt()).isEqualTo(passwordChangedAt);
    verify(credentials).save(credential);
  }

  @Test
  void verify_shouldNotRewriteCurrentHash() {
    UserEntity user = user(UserStatusEnum.ACTIVE);
    LocalCredentialEntity credential = new LocalCredentialEntity(
        user, "{argon2id}current-hash", NOW.minusSeconds(60));
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(credentials.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}current-hash")))
        .thenReturn(true);

    service.verify("Person@Example.test", "CorrectPassword1!".toCharArray(), NOW);

    verify(encoder, never()).encode(any(CharSequence.class));
    verify(credentials, never()).save(any());
  }

  private static UserEntity user(UserStatusEnum status) {
    UserEntity user = new UserEntity("person@example.test", "person@example.test", status);
    ReflectionTestUtils.setField(user, "id", 41L);
    return user;
  }
}
