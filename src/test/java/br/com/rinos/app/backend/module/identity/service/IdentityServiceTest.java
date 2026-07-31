package br.com.rinos.app.backend.module.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.backend.module.identity.entity.RegistrationEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.RegistrationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.RegistrationStatusEnum;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.RegistrationRepository;
import br.com.rinos.app.backend.module.identity.repository.UserRepository;

@DisplayName("Serviço interno de identidade")
class IdentityServiceTest {

  private static final Instant EXPIRES_AT = Instant.parse("2026-08-13T18:00:00Z");

  private UserRepository userRepository;
  private RegistrationRepository registrationRepository;
  private IdentityService service;

  /**
   * Prepara repositories isolados para cada comportamento.
   */
  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    registrationRepository = mock(RegistrationRepository.class);
    service = new IdentityService(
        userRepository,
        registrationRepository,
        new EmailNormalizationService());
  }

  /**
   * Cria usuário e cadastro pendentes usando a mesma transação coordenada pelo serviço.
   */
  @Test
  void createPendingIdentity_shouldPersistNormalizedUserAndRegistration_whenInputIsValid() {
    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(registrationRepository.save(any(RegistrationEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RegistrationEntity result = service.createPendingIdentity(
        " User@Example.COM ",
        RegistrationMethodEnum.LOCAL,
        EXPIRES_AT);

    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("User@Example.COM");
    assertThat(userCaptor.getValue().getNormalizedEmail()).isEqualTo("user@example.com");
    assertThat(userCaptor.getValue().getStatus())
        .isEqualTo(UserStatusEnum.PENDING_VERIFICATION);
    assertThat(result.getUser()).isSameAs(userCaptor.getValue());
    assertThat(result.getStatus()).isEqualTo(RegistrationStatusEnum.PENDING_VERIFICATION);
    assertThat(result.getMethod()).isEqualTo(RegistrationMethodEnum.LOCAL);
    assertThat(result.getExpiresAt()).isEqualTo(EXPIRES_AT);
  }

  /**
   * Consulta a identidade sempre pela mesma chave canônica.
   */
  @Test
  void findByEmail_shouldQueryNormalizedKey_whenEmailUsesDifferentCaseAndSpacing() {
    UserEntity user = user();
    when(userRepository.findByNormalizedEmail("user@example.com"))
        .thenReturn(Optional.of(user));

    Optional<UserEntity> result = service.findByEmail(" USER@EXAMPLE.COM ");

    assertThat(result).containsSame(user);
  }

  /**
   * Retorna somente o processo ainda pendente da identidade localizada.
   */
  @Test
  void findPendingRegistration_shouldReturnRegistration_whenBothStatesArePending() {
    UserEntity user = user();
    RegistrationEntity registration = new RegistrationEntity(
        user,
        RegistrationMethodEnum.GOOGLE,
        RegistrationStatusEnum.PENDING_VERIFICATION,
        EXPIRES_AT);
    when(userRepository.findByNormalizedEmailAndStatus(
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION)).thenReturn(Optional.of(user));
    when(registrationRepository.findByUserIdAndStatus(
        user.getId(),
        RegistrationStatusEnum.PENDING_VERIFICATION)).thenReturn(Optional.of(registration));

    Optional<RegistrationEntity> result =
        service.findPendingRegistration("USER@example.com");

    assertThat(result).containsSame(registration);
  }

  private static UserEntity user() {
    return new UserEntity(
        "user@example.com",
        "user@example.com",
        UserStatusEnum.PENDING_VERIFICATION);
  }
}
