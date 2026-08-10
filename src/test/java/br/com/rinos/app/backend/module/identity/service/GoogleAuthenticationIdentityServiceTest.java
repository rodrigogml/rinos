package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.GoogleAuthenticationIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.vo.GoogleAuthenticationIdentityVO;

@DisplayName("Localização da identidade Google para login")
class GoogleAuthenticationIdentityServiceTest {

  private static final String ISSUER = "https://accounts.google.com";
  private static final String SUBJECT = "stable-subject";
  private static final Instant NOW = Instant.parse("2026-08-10T22:00:00Z");

  @Test
  void resolve_shouldReturnActiveUser_usingOnlyIssuerAndSubject() {
    ExternalIdentityService externalIdentities = mock(ExternalIdentityService.class);
    ExternalIdentityEntity identity = activeIdentity(UserStatusEnum.ACTIVE);
    when(externalIdentities.findForUpdate(ISSUER, SUBJECT))
        .thenReturn(Optional.of(identity));
    GoogleAuthenticationIdentityService service =
        new GoogleAuthenticationIdentityService(externalIdentities);

    GoogleAuthenticationIdentityVO result = service.resolve(ISSUER, SUBJECT);

    assertThat(result.status()).isEqualTo(GoogleAuthenticationIdentityStatusEnum.MATCHED);
    assertThat(result.userId()).isEqualTo(41L);
    verify(externalIdentities).findForUpdate(ISSUER, SUBJECT);
    verifyNoMoreInteractions(externalIdentities);
  }

  @Test
  void resolve_shouldReturnNotFound_withoutLookingUpOrLinkingByEmail() {
    ExternalIdentityService externalIdentities = mock(ExternalIdentityService.class);
    when(externalIdentities.findForUpdate(ISSUER, SUBJECT)).thenReturn(Optional.empty());
    GoogleAuthenticationIdentityService service =
        new GoogleAuthenticationIdentityService(externalIdentities);

    GoogleAuthenticationIdentityVO result = service.resolve(ISSUER, SUBJECT);

    assertThat(result.status()).isEqualTo(GoogleAuthenticationIdentityStatusEnum.NOT_FOUND);
    assertThat(result.userId()).isNull();
    verify(externalIdentities).findForUpdate(ISSUER, SUBJECT);
    verifyNoMoreInteractions(externalIdentities);
  }

  @ParameterizedTest
  @EnumSource(value = UserStatusEnum.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
  void resolve_shouldRejectLink_whenOwnerIsNotActive(UserStatusEnum userStatus) {
    ExternalIdentityService externalIdentities = mock(ExternalIdentityService.class);
    when(externalIdentities.findForUpdate(ISSUER, SUBJECT))
        .thenReturn(Optional.of(activeIdentity(userStatus)));
    GoogleAuthenticationIdentityService service =
        new GoogleAuthenticationIdentityService(externalIdentities);

    GoogleAuthenticationIdentityVO result = service.resolve(ISSUER, SUBJECT);

    assertThat(result.status()).isEqualTo(GoogleAuthenticationIdentityStatusEnum.REJECTED);
    assertThat(result.userId()).isNull();
  }

  @Test
  void resolve_shouldRejectPendingLink_evenWhenOwnerIsActive() {
    ExternalIdentityService externalIdentities = mock(ExternalIdentityService.class);
    UserEntity user = user(UserStatusEnum.ACTIVE);
    ExternalIdentityEntity pending = new ExternalIdentityEntity(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        SUBJECT,
        NOW);
    when(externalIdentities.findForUpdate(ISSUER, SUBJECT))
        .thenReturn(Optional.of(pending));
    GoogleAuthenticationIdentityService service =
        new GoogleAuthenticationIdentityService(externalIdentities);

    GoogleAuthenticationIdentityVO result = service.resolve(ISSUER, SUBJECT);

    assertThat(result.status()).isEqualTo(GoogleAuthenticationIdentityStatusEnum.REJECTED);
    assertThat(result.userId()).isNull();
  }

  private static ExternalIdentityEntity activeIdentity(UserStatusEnum userStatus) {
    ExternalIdentityEntity identity = new ExternalIdentityEntity(
        user(userStatus),
        ExternalIdentityProviderEnum.GOOGLE,
        ISSUER,
        SUBJECT,
        NOW);
    identity.setStatus(br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum.ACTIVE);
    identity.setActivatedAt(NOW);
    return identity;
  }

  private static UserEntity user(UserStatusEnum status) {
    UserEntity user = new UserEntity("rinos@example.test", "rinos@example.test", status);
    ReflectionTestUtils.setField(user, "id", 41L);
    return user;
  }
}
