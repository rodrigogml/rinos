package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.LocalCredentialEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.LocalCredentialStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.LocalCredentialRepository;

@DisplayName("Credencial local da identidade")
class LocalCredentialServiceTest {

  private static final Instant INVALIDATED_AT = Instant.parse("2026-07-29T18:00:00Z");

  private LocalCredentialRepository repository;
  private LocalCredentialService service;
  private UserEntity user;

  /**
   * Prepara uma identidade persistida e repository isolado.
   */
  @BeforeEach
  void setUp() {
    repository = mock(LocalCredentialRepository.class);
    service = new LocalCredentialService(repository);
    user = new UserEntity(
        "user@example.com",
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
    ReflectionTestUtils.setField(user, "id", 41L);
  }

  /**
   * Cria uma credencial ativa quando a identidade ainda não possui senha local.
   */
  @Test
  void replace_shouldCreateActiveCredential_whenUserHasNoCredential() {
    when(repository.findByUserIdForUpdate(41L)).thenReturn(Optional.empty());

    service.replace(user, "{argon2}encoded-value");

    verify(repository).save(any(LocalCredentialEntity.class));
  }

  /**
   * Substitui o hash sem retornar a credencial ou o segredo ao chamador.
   */
  @Test
  void replace_shouldUpdateExistingCredential_whenUserAlreadyHasCredential() {
    LocalCredentialEntity credential =
        new LocalCredentialEntity(user, "{argon2}old-value");
    when(repository.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));

    service.replace(user, "{argon2}new-value");

    assertThat(credential.getPasswordHash()).isEqualTo("{argon2}new-value");
    assertThat(credential.getStatus()).isEqualTo(LocalCredentialStatusEnum.ACTIVE);
    verify(repository).save(credential);
  }

  /**
   * Invalida, sincroniza e remove a credencial na mesma operação Google.
   */
  @Test
  void invalidateAndRemoveForGoogle_shouldDeleteCredential_whenLocalCredentialExists() {
    LocalCredentialEntity credential =
        new LocalCredentialEntity(user, "{argon2}sensitive-hash");
    when(repository.findByUserIdForUpdate(41L)).thenReturn(Optional.of(credential));

    boolean removed = service.invalidateAndRemoveForGoogle(41L, INVALIDATED_AT);

    assertThat(removed).isTrue();
    assertThat(credential.getStatus()).isEqualTo(LocalCredentialStatusEnum.INVALIDATED);
    assertThat(credential.getInvalidatedAt()).isEqualTo(INVALIDATED_AT);
    assertThat(credential.toString()).doesNotContain("sensitive-hash");
    verify(repository).saveAndFlush(credential);
    verify(repository).delete(credential);
    verify(repository).flush();
  }

  /**
   * Mantém a operação idempotente quando não existe credencial local.
   */
  @Test
  void invalidateAndRemoveForGoogle_shouldReturnFalse_whenCredentialDoesNotExist() {
    when(repository.findByUserIdForUpdate(41L)).thenReturn(Optional.empty());

    boolean removed = service.invalidateAndRemoveForGoogle(41L, INVALIDATED_AT);

    assertThat(removed).isFalse();
    verify(repository, never()).delete(any());
  }
}
