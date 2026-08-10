package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.GoogleAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.enums.GoogleIdentityResolutionStatusEnum;
import br.com.rinos.app.api.facade.GoogleAuthenticationFacade;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.GoogleAuthenticationResultVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;

@DisplayName("Adapter da identidade externa validada pelo RFW")
class RFWExternalIdentityResolverAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-10T15:00:00Z");
  private GoogleAuthenticationFacade authentication;
  private GoogleIdentityResolutionFacade registration;
  private RFWExternalIdentityResolverAdapter adapter;

  @BeforeEach
  void setUp() {
    authentication = mock(GoogleAuthenticationFacade.class);
    registration = mock(GoogleIdentityResolutionFacade.class);
    adapter = new RFWExternalIdentityResolverAdapter(
        authentication,
        registration,
        new RFWAuthenticationOutcomeAdapter(),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void resolve_shouldMapExistingIdentityThroughCommonMfaChallenge() {
    AuthenticationOrchestrationResultVO challenge = new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.CHALLENGE_REQUIRED,
        "opaque-flow",
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(AuthenticationMethodEnum.TOTP, AuthenticationMethodEnum.PASSKEY),
        List.of(new AuthenticationMethodEvidenceVO(
            AuthenticationMethodEnum.GOOGLE, NOW, null)),
        Set.of(),
        false,
        NOW.plusSeconds(300),
        java.util.UUID.randomUUID());
    when(authentication.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleAuthenticationResultVO.orchestrated(challenge)));

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(identity())
        .toCompletableFuture().join();

    ArgumentCaptor<GoogleAuthenticationRequestDTO> request =
        ArgumentCaptor.forClass(GoogleAuthenticationRequestDTO.class);
    verify(authentication).authenticate(request.capture());
    assertThat(request.getValue().issuer()).isEqualTo("https://accounts.google.com");
    assertThat(request.getValue().subject()).isEqualTo("subject-1");
    assertThat(request.getValue().validatedAt()).isEqualTo(NOW);
    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.CHALLENGE_REQUIRED);
    assertThat(outcome.challenge().allowedMethods()).containsExactlyInAnyOrder(
        RFWAuthenticationMethodEnum.TOTP, RFWAuthenticationMethodEnum.PASSKEY);
    verify(registration, never()).resolve(any());
  }

  @Test
  void resolve_shouldMapLegalGateWithoutPublishingPartialAuthentication() {
    AuthenticationOrchestrationResultVO legal = new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
        "opaque-legal-flow",
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(new AuthenticationMethodEvidenceVO(
            AuthenticationMethodEnum.GOOGLE, NOW, null)),
        Set.of("11"),
        false,
        NOW.plusSeconds(300),
        java.util.UUID.randomUUID());
    when(authentication.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleAuthenticationResultVO.orchestrated(legal)));

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(identity())
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(
        RFWAccessStatusEnum.AUTHENTICATION_CONSENT_REQUIRED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.authenticationConsent().legalDocumentIds()).containsExactly("11");
    verify(registration, never()).resolve(any());
  }

  @Test
  void resolve_shouldContinueRegistrationOnlyWhenStableIdentityIsAbsent() {
    Instant expiresAt = NOW.plusSeconds(300);
    when(authentication.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleAuthenticationResultVO.identityNotFound()));
    when(registration.resolve(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleIdentityResolutionResultVO.continuation(
            "opaque-reference", "google", "person@example.com", expiresAt)));

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(identity())
        .toCompletableFuture().join();

    ArgumentCaptor<GoogleIdentityResolutionRequestVO> request =
        ArgumentCaptor.forClass(GoogleIdentityResolutionRequestVO.class);
    verify(registration).resolve(request.capture());
    assertThat(request.getValue())
        .extracting(
            GoogleIdentityResolutionRequestVO::providerId,
            GoogleIdentityResolutionRequestVO::issuer,
            GoogleIdentityResolutionRequestVO::subject,
            GoogleIdentityResolutionRequestVO::email,
            GoogleIdentityResolutionRequestVO::emailVerified)
        .containsExactly(
            "google", "https://accounts.google.com", "subject-1",
            "person@example.com", true);
    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.EXTERNAL_REGISTRATION_REQUIRED);
    assertThat(outcome.externalRegistration().registrationReference())
        .isEqualTo("opaque-reference");
  }

  @Test
  void resolve_shouldNotFallBackToRegistrationForRejectedLinkedIdentity() {
    when(authentication.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleAuthenticationResultVO.orchestrated(new AuthenticationOrchestrationResultVO(
            AuthenticationOrchestrationStatusEnum.REJECTED,
            null, null, null, Set.of(), List.of(), Set.of(), false, null, null))));

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(identity())
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("authentication.credentials.invalid");
    verify(registration, never()).resolve(any());
  }

  @Test
  void resolve_shouldKeepRegistrationFailuresNeutralAndWithoutAuthentication() {
    when(authentication.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleAuthenticationResultVO.identityNotFound()));
    when(registration.resolve(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleIdentityResolutionResultVO.of(
            GoogleIdentityResolutionStatusEnum.EXTERNAL_IDENTITY_CONFLICT)));

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(identity())
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().messageKey()).isEqualTo("registration.google.identity-conflict");
  }

  private static RFWVerifiedExternalIdentityVO identity() {
    return new RFWVerifiedExternalIdentityVO(
        "google",
        "subject-1",
        "person@example.com",
        true,
        Map.of(
            "iss", "https://accounts.google.com",
            "profile", "claim-that-must-not-cross-the-api"));
  }
}
