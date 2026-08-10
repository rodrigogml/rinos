package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.dto.PasskeyAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.backend.module.identity.entity.PasskeyUserEntity;
import br.com.rinos.app.backend.module.identity.entity.UserEntity;
import br.com.rinos.app.backend.module.identity.enums.UserStatusEnum;
import br.com.rinos.app.backend.module.identity.repository.PasskeyUserRepository;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSecondFactorPolicyService;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

@DisplayName("Fachada de autenticação por passkey")
class PasskeyAuthenticationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("8aef82fd-8756-42d4-8c1a-60a9782f57b7");
  private static final byte[] USER_HANDLE = new byte[32];
  private PasskeyUserRepository passkeyUsers;
  private AuthenticationMethodAvailabilityService availability;
  private AuthenticationOrchestrationFacade orchestration;
  private PasskeyAuthenticationFacadeImpl facade;

  @BeforeEach
  void setUp() {
    passkeyUsers = mock(PasskeyUserRepository.class);
    availability = mock(AuthenticationMethodAvailabilityService.class);
    orchestration = mock(AuthenticationOrchestrationFacade.class);
    facade = new PasskeyAuthenticationFacadeImpl(
        passkeyUsers,
        availability,
        new AuthenticationSecondFactorPolicyService(),
        orchestration,
        new AuthenticationMfaPropertiesConfig(
            Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void authenticate_shouldRejectExpiredValidatedIdentity_withoutResolvingOwner() {
    AuthenticationOrchestrationResultVO result = facade.authenticate(
        request(NOW.minus(Duration.ofMinutes(5)).minusNanos(1)))
        .toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(passkeyUsers, never()).findByUserHandle(any());
  }

  @Test
  void authenticate_shouldRejectUnknownHandle_withoutOpeningFlow() {
    when(passkeyUsers.findByUserHandle(any())).thenReturn(Optional.empty());

    AuthenticationOrchestrationResultVO result = facade.authenticate(request(NOW))
        .toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(orchestration, never()).start(any());
  }

  @Test
  void authenticate_shouldStartPhishingResistantPrimaryThroughCommonOrchestrator() {
    UserEntity user = mock(UserEntity.class);
    when(user.getId()).thenReturn(41L);
    when(user.getStatus()).thenReturn(UserStatusEnum.ACTIVE);
    PasskeyUserEntity owner = mock(PasskeyUserEntity.class);
    when(owner.getUser()).thenReturn(user);
    when(passkeyUsers.findByUserHandle(any())).thenReturn(Optional.of(owner));
    when(availability.availableMethods(41L)).thenReturn(Set.of(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSKEY,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.TOTP,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.RECOVERY_CODE));
    when(orchestration.start(any())).thenReturn(mock(AuthenticationOrchestrationResultVO.class));

    facade.authenticate(request(NOW)).toCompletableFuture().join();

    ArgumentCaptor<AuthenticationOrchestrationStartDTO> captor =
        ArgumentCaptor.forClass(AuthenticationOrchestrationStartDTO.class);
    verify(orchestration).start(captor.capture());
    AuthenticationOrchestrationStartDTO command = captor.getValue();
    assertThat(command.primaryMethod()).isEqualTo(AuthenticationMethodEnum.PASSKEY);
    assertThat(command.userVerification()).isTrue();
    assertThat(command.requiredAssurance()).isEqualTo(AuthenticationAssuranceEnum.MULTI_FACTOR);
    assertThat(command.permittedMethods()).containsExactlyInAnyOrder(
        AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.RECOVERY_CODE);
    assertThat(command.persistentLoginRequested()).isFalse();
    assertThat(command.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
  }

  private static PasskeyAuthenticationRequestDTO request(Instant validatedAt) {
    return new PasskeyAuthenticationRequestDTO(USER_HANDLE, validatedAt, CORRELATION_ID);
  }

  private static AuthenticationOrchestrationResultVO terminal(
      AuthenticationOrchestrationStatusEnum status) {
    return new AuthenticationOrchestrationResultVO(
        status, null, null, null, Set.of(), List.of(), Set.of(), false, null, CORRELATION_ID);
  }
}
