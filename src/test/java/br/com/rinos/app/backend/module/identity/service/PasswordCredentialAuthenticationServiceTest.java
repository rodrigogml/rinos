package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    when(normalization.normalize("Person@Example.test"))
        .thenReturn(new NormalizedEmailVO("Person@Example.test", "person@example.test"));
  }

  @Test
  void verify_shouldUseSentinelHashAndErasePassword_whenIdentityDoesNotExist() {
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.empty());
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}sentinel")))
        .thenReturn(false);
    char[] password = "WrongPassword1!".toCharArray();

    OptionalLong result = service.verify("Person@Example.test", password, NOW);

    assertThat(result).isEmpty();
    assertThat(password).containsOnly('\0');
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}sentinel"));
    verify(credentials, never()).findByUserIdForUpdate(any());
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
  void verify_shouldStillPaySentinelCostForMalformedIdentifier() {
    when(normalization.normalize("invalid"))
        .thenThrow(new IllegalArgumentException("invalid email"));
    when(encoder.matches(any(CharSequence.class), eq("{argon2id}sentinel")))
        .thenReturn(false);

    OptionalLong result = service.verify("invalid", "Password1!".toCharArray(), NOW);

    assertThat(result).isEmpty();
    verify(encoder).matches(any(CharSequence.class), eq("{argon2id}sentinel"));
    verify(users, never()).findByNormalizedEmailForUpdate(any());
  }

  private static UserEntity user(UserStatusEnum status) {
    UserEntity user = new UserEntity("person@example.test", "person@example.test", status);
    ReflectionTestUtils.setField(user, "id", 41L);
    return user;
  }
}
