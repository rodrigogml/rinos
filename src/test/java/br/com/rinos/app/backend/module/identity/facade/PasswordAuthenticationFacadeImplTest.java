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
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.dto.PasswordAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.PasswordAuthenticationResultVO;
import br.com.rinos.app.backend.module.identity.service.AuthenticationAbuseProtectionService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSecondFactorPolicyService;
import br.com.rinos.app.backend.module.identity.service.PasswordCredentialAuthenticationService;
import br.com.rinos.app.backend.module.identity.vo.AuthenticationAbuseDecisionVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

@DisplayName("Fachada de autenticação por senha")
class PasswordAuthenticationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("d9b36467-3bee-43d6-9619-86728ca5863a");
  private PasswordCredentialAuthenticationService credentials;
  private AuthenticationAbuseProtectionService abuseProtection;
  private AuthenticationMethodAvailabilityService availability;
  private AuthenticationOrchestrationFacade orchestration;
  private PasswordAuthenticationFacadeImpl facade;

  @BeforeEach
  void setUp() {
    credentials = mock(PasswordCredentialAuthenticationService.class);
    abuseProtection = mock(AuthenticationAbuseProtectionService.class);
    availability = mock(AuthenticationMethodAvailabilityService.class);
    orchestration = mock(AuthenticationOrchestrationFacade.class);
    facade = new PasswordAuthenticationFacadeImpl(
        credentials,
        abuseProtection,
        availability,
        new AuthenticationSecondFactorPolicyService(),
        orchestration,
        new AuthenticationMfaPropertiesConfig(
            Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void authenticate_shouldRejectInvalidCredentialWithoutOpeningFlow() {
    when(credentials.verify(any(), any(), any())).thenReturn(OptionalLong.empty());
    when(abuseProtection.registerFailure(any(), any(), any()))
        .thenReturn(AuthenticationAbuseDecisionVO.clear());

    PasswordAuthenticationResultVO result = facade.authenticate(request()).toCompletableFuture().join();

    assertThat(result.orchestration().status())
        .isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(availability, never()).availableMethods(any());
    verify(orchestration, never()).start(any());
  }

  @Test
  void authenticate_shouldExposeMostRestrictiveDecisionAfterCredentialFailure() {
    when(credentials.verify(any(), any(), any())).thenReturn(OptionalLong.empty());
    when(abuseProtection.registerFailure(
        "person@example.test", "198.51.100.12", NOW))
        .thenReturn(new AuthenticationAbuseDecisionVO(
            3, true, Duration.ofSeconds(4), NOW.plusSeconds(900)));

    PasswordAuthenticationResultVO result = facade.authenticate(request()).toCompletableFuture().join();

    assertThat(result.orchestration().status())
        .isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    assertThat(result.turnstileRequired()).isTrue();
    assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(4));
  }

  @Test
  void authenticate_shouldOpenSingleFactorFlowWhenNoMfaActivationExists() {
    when(credentials.verify(any(), any(), any())).thenReturn(OptionalLong.of(41L));
    when(availability.availableMethods(41L)).thenReturn(Set.of(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSKEY,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.GOOGLE));
    when(orchestration.start(any())).thenReturn(ready());

    facade.authenticate(request()).toCompletableFuture().join();

    ArgumentCaptor<AuthenticationOrchestrationStartDTO> captor =
        ArgumentCaptor.forClass(AuthenticationOrchestrationStartDTO.class);
    verify(orchestration).start(captor.capture());
    assertThat(captor.getValue().requiredAssurance())
        .isEqualTo(AuthenticationAssuranceEnum.SINGLE_FACTOR);
    assertThat(captor.getValue().permittedMethods()).isEmpty();
    assertThat(captor.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(300));
  }

  @Test
  void authenticate_shouldRequireMfaAndOfferOnlyIndependentAdditionalMethods() {
    when(credentials.verify(any(), any(), any())).thenReturn(OptionalLong.of(41L));
    when(availability.availableMethods(41L)).thenReturn(Set.of(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSWORD,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.GOOGLE,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSKEY,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.TOTP,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.RECOVERY_CODE));
    when(orchestration.start(any())).thenReturn(ready());

    facade.authenticate(request()).toCompletableFuture().join();

    ArgumentCaptor<AuthenticationOrchestrationStartDTO> captor =
        ArgumentCaptor.forClass(AuthenticationOrchestrationStartDTO.class);
    verify(orchestration).start(captor.capture());
    assertThat(captor.getValue().requiredAssurance())
        .isEqualTo(AuthenticationAssuranceEnum.MULTI_FACTOR);
    assertThat(captor.getValue().permittedMethods()).containsExactlyInAnyOrder(
        AuthenticationMethodEnum.TOTP,
        AuthenticationMethodEnum.RECOVERY_CODE);
    assertThat(captor.getValue().persistentLoginRequested()).isTrue();
  }

  private static PasswordAuthenticationRequestDTO request() {
    return new PasswordAuthenticationRequestDTO(
        "person@example.test",
        "Password1!".toCharArray(),
        true,
        null,
        "198.51.100.12",
        CORRELATION_ID);
  }

  private static AuthenticationOrchestrationResultVO ready() {
    return new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.READY,
        "opaque-flow",
        new br.com.rinos.app.api.vo.RinosUserPrincipalVO(41L, "person@example.test"),
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(new AuthenticationMethodEvidenceVO(AuthenticationMethodEnum.PASSWORD, NOW, null)),
        Set.of(),
        true,
        NOW.plusSeconds(300),
        CORRELATION_ID);
  }
}
