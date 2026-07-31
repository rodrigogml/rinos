package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.ExternalIdentityEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;
import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.ExternalIdentityRepository;

@DisplayName("Vínculo de identidade externa")
class ExternalIdentityServiceTest {

  @Test
  void createPending_shouldPersistOnlyStableProviderKeys() {
    ExternalIdentityRepository repository = mock(ExternalIdentityRepository.class);
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    ExternalIdentityService service = new ExternalIdentityService(repository);

    ExternalIdentityEntity result = service.createPending(
        user(),
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "stable-subject",
        Instant.parse("2026-07-29T18:00:00Z"));

    assertThat(result.getIssuer()).isEqualTo("https://accounts.google.com");
    assertThat(result.getSubject()).isEqualTo("stable-subject");
    assertThat(result.getStatus()).isEqualTo(ExternalIdentityStatusEnum.PENDING);
    verify(repository).save(result);
  }

  @Test
  void activate_shouldBeIdempotent() {
    ExternalIdentityEntity identity = new ExternalIdentityEntity(
        user(),
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "stable-subject",
        Instant.parse("2026-07-29T18:00:00Z"));
    ExternalIdentityService service =
        new ExternalIdentityService(mock(ExternalIdentityRepository.class));
    Instant activatedAt = Instant.parse("2026-07-29T18:05:00Z");

    service.activate(identity, activatedAt);
    service.activate(identity, Instant.parse("2026-07-29T18:10:00Z"));

    assertThat(identity.getStatus()).isEqualTo(ExternalIdentityStatusEnum.ACTIVE);
    assertThat(identity.getActivatedAt()).isEqualTo(activatedAt);
  }

  @Test
  void replacePending_shouldDeletePreviousCandidateBeforeCreatingNewOne() {
    ExternalIdentityRepository repository = mock(ExternalIdentityRepository.class);
    UserEntity user = persistedUser();
    ExternalIdentityEntity previous = new ExternalIdentityEntity(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "previous-subject",
        Instant.parse("2026-07-29T17:00:00Z"));
    when(repository.findByUserIdAndStatusForUpdate(
        41L,
        ExternalIdentityStatusEnum.PENDING)).thenReturn(List.of(previous));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    ExternalIdentityService service = new ExternalIdentityService(repository);

    ExternalIdentityEntity result = service.replacePending(
        user,
        ExternalIdentityProviderEnum.GOOGLE,
        "https://accounts.google.com",
        "new-subject",
        Instant.parse("2026-07-29T18:00:00Z"));

    assertThat(result.getSubject()).isEqualTo("new-subject");
    verify(repository).deleteAll(List.of(previous));
    verify(repository).flush();
    verify(repository).save(result);
  }

  @Test
  void findSinglePendingForUpdate_shouldRejectInconsistentMultipleCandidates() {
    ExternalIdentityRepository repository = mock(ExternalIdentityRepository.class);
    UserEntity user = persistedUser();
    when(repository.findByUserIdAndStatusForUpdate(
        41L,
        ExternalIdentityStatusEnum.PENDING)).thenReturn(List.of(
            new ExternalIdentityEntity(
                user,
                ExternalIdentityProviderEnum.GOOGLE,
                "issuer",
                "subject-1",
                Instant.now()),
            new ExternalIdentityEntity(
                user,
                ExternalIdentityProviderEnum.GOOGLE,
                "issuer",
                "subject-2",
                Instant.now())));
    ExternalIdentityService service = new ExternalIdentityService(repository);

    assertThatThrownBy(() -> service.findSinglePendingForUpdate(41L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void createPending_shouldRejectBlankOrOversizedKeys() {
    ExternalIdentityService service =
        new ExternalIdentityService(mock(ExternalIdentityRepository.class));

    assertThatThrownBy(() -> service.createPending(
        user(),
        ExternalIdentityProviderEnum.GOOGLE,
        " ",
        "subject",
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.createPending(
        user(),
        ExternalIdentityProviderEnum.GOOGLE,
        "issuer",
        "s".repeat(256),
        Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static UserEntity user() {
    return new UserEntity(
        "user@example.com",
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
  }

  private static UserEntity persistedUser() {
    UserEntity user = user();
    ReflectionTestUtils.setField(user, "id", 41L);
    return user;
  }
}
