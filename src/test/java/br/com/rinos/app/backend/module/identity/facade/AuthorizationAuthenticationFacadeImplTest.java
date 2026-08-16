package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionEntity;
import br.com.rinos.app.backend.module.identity.entity.AuthSessionMethodEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionMethodRepository;
import br.com.rinos.app.backend.module.identity.repository.AuthSessionRepository;
import br.com.rinos.app.backend.module.identity.service.IdentityReferenceService;

class AuthorizationAuthenticationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-16T12:00:00Z");

  @Test
  void resolve_shouldRevalidatePersistentSessionAndMapCurrentAssurance() {
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    AuthSessionMethodRepository methods = mock(AuthSessionMethodRepository.class);
    IdentityReferenceService references = mock(IdentityReferenceService.class);
    AuthSessionEntity session = activeSession(11L);
    UUID reference = UUID.randomUUID();
    byte[] encoded = new byte[] {1, 2, 3};
    when(references.encode(reference)).thenReturn(encoded);
    when(sessions.findByPublicReference(encoded)).thenReturn(Optional.of(session));
    AuthSessionMethodEntity password = method(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD);
    AuthSessionMethodEntity totp = method(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.TOTP);
    when(methods.findBySessionIdOrderByFactorOrder(90L)).thenReturn(List.of(password, totp));
    AuthorizationAuthenticationFacadeImpl facade =
        new AuthorizationAuthenticationFacadeImpl(sessions, methods, references);

    var assurance = facade.resolve(11L, reference.toString(), NOW).orElseThrow();

    assertThat(assurance.level()).isEqualTo(AuthenticationAssuranceEnum.MULTI_FACTOR);
    assertThat(assurance.methods())
        .containsExactlyInAnyOrder(AuthenticationMethodEnum.PASSWORD, AuthenticationMethodEnum.TOTP);
    assertThat(assurance.authenticatedAt()).isEqualTo(NOW.minusSeconds(600));
    assertThat(assurance.lastStrongAuthenticationAt()).isEqualTo(NOW.minusSeconds(60));
  }

  @Test
  void resolve_shouldFailClosedForDifferentIdentityOrExpiredSession() {
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    AuthSessionMethodRepository methods = mock(AuthSessionMethodRepository.class);
    IdentityReferenceService references = mock(IdentityReferenceService.class);
    AuthSessionEntity session = activeSession(22L);
    UUID reference = UUID.randomUUID();
    byte[] encoded = new byte[] {4, 5, 6};
    when(references.encode(reference)).thenReturn(encoded);
    when(sessions.findByPublicReference(encoded)).thenReturn(Optional.of(session));
    AuthorizationAuthenticationFacadeImpl facade =
        new AuthorizationAuthenticationFacadeImpl(sessions, methods, references);

    assertThat(facade.resolve(11L, reference.toString(), NOW)).isEmpty();
    assertThat(facade.resolve(11L, "not-a-reference", NOW)).isEmpty();
    verify(methods, never()).findBySessionIdOrderByFactorOrder(org.mockito.ArgumentMatchers.any());
  }

  private static AuthSessionEntity activeSession(long userId) {
    UserEntity user = mock(UserEntity.class);
    when(user.getId()).thenReturn(userId);
    when(user.getStatus()).thenReturn(UserStatusEnum.ACTIVE);
    AuthSessionEntity session = mock(AuthSessionEntity.class);
    when(session.getId()).thenReturn(90L);
    when(session.getUser()).thenReturn(user);
    when(session.getStatus()).thenReturn(AuthSessionStatusEnum.ACTIVE);
    when(session.getAssuranceLevel()).thenReturn(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum.MULTI_FACTOR);
    when(session.getAuthenticatedAt()).thenReturn(NOW.minusSeconds(600));
    when(session.getLastStrongAuthAt()).thenReturn(NOW.minusSeconds(60));
    when(session.getAbsoluteExpiresAt()).thenReturn(NOW.plusSeconds(3600));
    when(session.getIdleExpiresAt()).thenReturn(NOW.plusSeconds(600));
    return session;
  }

  private static AuthSessionMethodEntity method(
      br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum value) {
    AuthSessionMethodEntity method = mock(AuthSessionMethodEntity.class);
    when(method.getMethod()).thenReturn(value);
    return method;
  }
}
