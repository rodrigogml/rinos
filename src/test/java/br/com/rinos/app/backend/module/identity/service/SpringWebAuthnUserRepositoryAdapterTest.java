package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

@DisplayName("Adapter Spring WebAuthn do owner global")
class SpringWebAuthnUserRepositoryAdapterTest {

  private UserRepository users;
  private PasskeyUserRepository owners;
  private SpringWebAuthnUserRepositoryAdapter adapter;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    owners = mock(PasskeyUserRepository.class);
    adapter = new SpringWebAuthnUserRepositoryAdapter(
        users, owners, new EmailNormalizationService());
    user = new UserEntity("Person@Example.test", "person@example.test", UserStatusEnum.ACTIVE);
    ReflectionTestUtils.setField(user, "id", 41L);
  }

  @Test
  void findByUsername_shouldExposeStableHandleForActiveIdentity() {
    byte[] handle = handle((byte) 3);
    PasskeyUserEntity owner = new PasskeyUserEntity(user, handle);
    when(users.findByNormalizedEmailAndStatus("person@example.test", UserStatusEnum.ACTIVE))
        .thenReturn(Optional.of(user));
    when(owners.findByUserId(41L)).thenReturn(Optional.of(owner));

    PublicKeyCredentialUserEntity result = adapter.findByUsername("Person@Example.test");

    assertThat(result.getName()).isEqualTo("Person@Example.test");
    assertThat(result.getDisplayName()).isEqualTo("Person@Example.test");
    assertThat(result.getId().getBytes()).containsExactly(handle);
  }

  @Test
  void save_shouldCreateOwnerWithoutCreatingOrChangingIdentity() {
    byte[] handle = handle((byte) 5);
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(owners.findByUserIdForUpdate(41L)).thenReturn(Optional.empty());
    when(owners.findByUserHandle(handle)).thenReturn(Optional.empty());
    PublicKeyCredentialUserEntity springUser = ImmutablePublicKeyCredentialUserEntity.builder()
        .name("Person@Example.test")
        .displayName("Person@Example.test")
        .id(new Bytes(handle))
        .build();

    adapter.save(springUser);

    ArgumentCaptor<PasskeyUserEntity> saved = ArgumentCaptor.forClass(PasskeyUserEntity.class);
    verify(owners).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getUser()).isSameAs(user);
    assertThat(saved.getValue().getUserHandle()).containsExactly(handle);
  }

  @Test
  void save_shouldRejectReplacementOfExistingStableHandle() {
    when(users.findByNormalizedEmailForUpdate("person@example.test"))
        .thenReturn(Optional.of(user));
    when(owners.findByUserIdForUpdate(41L))
        .thenReturn(Optional.of(new PasskeyUserEntity(user, handle((byte) 1))));
    PublicKeyCredentialUserEntity replacement = ImmutablePublicKeyCredentialUserEntity.builder()
        .name("Person@Example.test")
        .displayName("Person@Example.test")
        .id(new Bytes(handle((byte) 2)))
        .build();

    assertThatThrownBy(() -> adapter.save(replacement))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("WebAuthn user handle is immutable");
  }

  @Test
  void delete_shouldFailClosedInsteadOfDeletingGlobalIdentity() {
    assertThatThrownBy(() -> adapter.delete(new Bytes(handle((byte) 7))))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("passkey management");
  }

  private static byte[] handle(byte value) {
    byte[] result = new byte[32];
    java.util.Arrays.fill(result, value);
    return result;
  }
}
