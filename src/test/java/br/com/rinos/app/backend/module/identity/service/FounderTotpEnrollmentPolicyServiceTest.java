package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.TotpFactorStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.TotpFactorRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;
import br.com.rinos.app.config.AccessBootstrapPropertiesConfig;

@DisplayName("Política de enrollment TOTP do fundador")
class FounderTotpEnrollmentPolicyServiceTest {

  @Test
  void requiresEnrollment_shouldRestrictOnlyConfiguredActiveIdentityWithoutTotp() {
    UserRepository users = mock(UserRepository.class);
    TotpFactorRepository factors = mock(TotpFactorRepository.class);
    UserEntity founder = user(41L, "admin@rinos.com.br", UserStatusEnum.ACTIVE);
    when(users.findById(41L)).thenReturn(Optional.of(founder));
    when(factors.countByUserIdAndStatus(41L, TotpFactorStatusEnum.ACTIVE)).thenReturn(0L);
    FounderTotpEnrollmentPolicyService service = service(users, factors);

    assertThat(service.requiresEnrollment(41L)).isTrue();
  }

  @Test
  void requiresEnrollment_shouldReleaseFounderAfterTotpConfirmation() {
    UserRepository users = mock(UserRepository.class);
    TotpFactorRepository factors = mock(TotpFactorRepository.class);
    when(users.findById(41L)).thenReturn(Optional.of(user(
        41L, "admin@rinos.com.br", UserStatusEnum.ACTIVE)));
    when(factors.countByUserIdAndStatus(41L, TotpFactorStatusEnum.ACTIVE)).thenReturn(1L);

    assertThat(service(users, factors).requiresEnrollment(41L)).isFalse();
  }

  @Test
  void requiresEnrollment_shouldNotRevealOrRestrictDifferentIdentity() {
    UserRepository users = mock(UserRepository.class);
    TotpFactorRepository factors = mock(TotpFactorRepository.class);
    when(users.findById(42L)).thenReturn(Optional.of(user(
        42L, "other@rinos.com.br", UserStatusEnum.ACTIVE)));

    assertThat(service(users, factors).requiresEnrollment(42L)).isFalse();
  }

  private static FounderTotpEnrollmentPolicyService service(
      UserRepository users,
      TotpFactorRepository factors) {
    return new FounderTotpEnrollmentPolicyService(
        new AccessBootstrapPropertiesConfig("admin@rinos.com.br"),
        new EmailNormalizationService(),
        users,
        factors);
  }

  private static UserEntity user(long id, String email, UserStatusEnum status) {
    UserEntity user = new UserEntity(email, email, status);
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
