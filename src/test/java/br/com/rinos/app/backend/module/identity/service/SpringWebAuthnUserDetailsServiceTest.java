package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

@DisplayName("Resolução técnica do usuário WebAuthn")
class SpringWebAuthnUserDetailsServiceTest {

  @Test
  void load_shouldResolveOnlyActiveUserWithNoReusablePassword() {
    UserRepository users = mock(UserRepository.class);
    UserEntity user = mock(UserEntity.class);
    when(user.getEmail()).thenReturn("Person@Example.test");
    when(users.findByNormalizedEmailAndStatus(
        "person@example.test", UserStatusEnum.ACTIVE)).thenReturn(Optional.of(user));
    SpringWebAuthnUserDetailsService service = new SpringWebAuthnUserDetailsService(
        users, new EmailNormalizationService());

    var first = service.loadUserByUsername(" Person@Example.test ");
    var second = service.loadUserByUsername("Person@Example.test");

    assertThat(first.getUsername()).isEqualTo("Person@Example.test");
    assertThat(first.getAuthorities()).isEmpty();
    assertThat(first.getPassword()).startsWith("{noop}").isNotEqualTo(second.getPassword());
  }

  @Test
  void load_shouldUseNeutralNotFoundForInvalidOrUnknownIdentity() {
    UserRepository users = mock(UserRepository.class);
    SpringWebAuthnUserDetailsService service = new SpringWebAuthnUserDetailsService(
        users, new EmailNormalizationService());

    assertThatThrownBy(() -> service.loadUserByUsername("invalid"))
        .isInstanceOf(UsernameNotFoundException.class);
    assertThatThrownBy(() -> service.loadUserByUsername("unknown@example.test"))
        .isInstanceOf(UsernameNotFoundException.class);
  }
}
