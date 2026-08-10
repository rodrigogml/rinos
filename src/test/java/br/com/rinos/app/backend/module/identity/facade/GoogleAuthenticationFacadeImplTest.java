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
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.dto.GoogleAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.enums.GoogleAuthenticationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationOrchestrationFacade;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.GoogleAuthenticationResultVO;
import br.com.rinos.app.backend.module.identity.enums.GoogleAuthenticationIdentityStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationMethodAvailabilityService;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSecondFactorPolicyService;
import br.com.rinos.app.backend.module.identity.service.GoogleAuthenticationIdentityService;
import br.com.rinos.app.backend.module.identity.vo.GoogleAuthenticationIdentityVO;
import br.com.rinos.app.config.AuthenticationMfaPropertiesConfig;

@DisplayName("Fachada de autenticação Google")
class GoogleAuthenticationFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-10T15:00:00Z");
  private static final UUID CORRELATION_ID =
      UUID.fromString("168769bd-e9e1-47d6-a74c-fcb5718ae689");
  private GoogleAuthenticationIdentityService identities;
  private AuthenticationMethodAvailabilityService availability;
  private AuthenticationOrchestrationFacade orchestration;
  private GoogleAuthenticationFacadeImpl facade;

  @BeforeEach
  void setUp() {
    identities = mock(GoogleAuthenticationIdentityService.class);
    availability = mock(AuthenticationMethodAvailabilityService.class);
    orchestration = mock(AuthenticationOrchestrationFacade.class);
    facade = new GoogleAuthenticationFacadeImpl(
        identities,
        availability,
        new AuthenticationSecondFactorPolicyService(),
        orchestration,
        new AuthenticationMfaPropertiesConfig(
            Duration.ofMinutes(5), 5, Duration.ofMinutes(1), 3, Duration.ofMinutes(15)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void authenticate_shouldReturnAbsentWithoutOpeningFlow_whenStableIdentityDoesNotExist() {
    when(identities.resolve("https://accounts.google.com", "subject-1"))
        .thenReturn(GoogleAuthenticationIdentityVO.of(
            GoogleAuthenticationIdentityStatusEnum.NOT_FOUND));

    GoogleAuthenticationResultVO result = facade.authenticate(request(NOW))
        .toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(GoogleAuthenticationStatusEnum.IDENTITY_NOT_FOUND);
    assertThat(result.orchestration()).isNull();
    verify(availability, never()).availableMethods(any());
    verify(orchestration, never()).start(any());
  }

  @Test
  void authenticate_shouldRejectInactiveStableIdentityWithoutRegistrationFallback() {
    when(identities.resolve("https://accounts.google.com", "subject-1"))
        .thenReturn(GoogleAuthenticationIdentityVO.of(
            GoogleAuthenticationIdentityStatusEnum.REJECTED));

    GoogleAuthenticationResultVO result = facade.authenticate(request(NOW))
        .toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(GoogleAuthenticationStatusEnum.ORCHESTRATED);
    assertThat(result.orchestration().status())
        .isEqualTo(AuthenticationOrchestrationStatusEnum.REJECTED);
    verify(orchestration, never()).start(any());
  }

  @Test
  void authenticate_shouldRequireIndependentMfaAndExcludeSameEmailChannel() {
    when(identities.resolve("https://accounts.google.com", "subject-1"))
        .thenReturn(GoogleAuthenticationIdentityVO.matched(41L));
    when(availability.availableMethods(41L)).thenReturn(Set.of(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.GOOGLE,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.EMAIL_CODE,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.TOTP,
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.PASSKEY));
    when(orchestration.start(any())).thenReturn(terminal(
        AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED,
        Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY),
        Set.of()));

    facade.authenticate(request(NOW)).toCompletableFuture().join();

    ArgumentCaptor<AuthenticationOrchestrationStartDTO> captor =
        ArgumentCaptor.forClass(AuthenticationOrchestrationStartDTO.class);
    verify(orchestration).start(captor.capture());
    AuthenticationOrchestrationStartDTO command = captor.getValue();
    assertThat(command.primaryMethod()).isEqualTo(AuthenticationMethodEnum.GOOGLE);
    assertThat(command.requiredAssurance()).isEqualTo(AuthenticationAssuranceEnum.MULTI_FACTOR);
    assertThat(command.permittedMethods()).containsExactlyInAnyOrder(
        AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY);
    assertThat(command.permittedMethods()).doesNotContain(AuthenticationMethodEnum.EMAIL_CODE);
    assertThat(command.persistentLoginRequested()).isFalse();
    assertThat(command.verifiedAt()).isEqualTo(NOW);
    assertThat(command.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
  }

  @Test
  void authenticate_shouldPreserveLegalGateFromCommonOrchestrator() {
    when(identities.resolve("https://accounts.google.com", "subject-1"))
        .thenReturn(GoogleAuthenticationIdentityVO.matched(41L));
    when(availability.availableMethods(41L)).thenReturn(Set.of(
        br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum.GOOGLE));
    AuthenticationOrchestrationResultVO legalGate = terminal(
        AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
        Set.of(), Set.of("11", "12"));
    when(orchestration.start(any())).thenReturn(legalGate);

    GoogleAuthenticationResultVO result = facade.authenticate(request(NOW))
        .toCompletableFuture().join();

    assertThat(result.status()).isEqualTo(GoogleAuthenticationStatusEnum.ORCHESTRATED);
    assertThat(result.orchestration()).isSameAs(legalGate);
  }

  private static GoogleAuthenticationRequestDTO request(Instant validatedAt) {
    return new GoogleAuthenticationRequestDTO(
        "https://accounts.google.com", "subject-1", validatedAt, CORRELATION_ID);
  }

  private static AuthenticationOrchestrationResultVO terminal(
      AuthenticationOrchestrationStatusEnum status,
      Set<AuthenticationMethodEnum> permitted,
      Set<String> missingLegalDocuments) {
    return new AuthenticationOrchestrationResultVO(
        status,
        "opaque-flow",
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        permitted,
        List.of(new AuthenticationMethodEvidenceVO(
            AuthenticationMethodEnum.GOOGLE, NOW, null)),
        missingLegalDocuments,
        false,
        NOW.plus(Duration.ofMinutes(5)),
        CORRELATION_ID);
  }
}
