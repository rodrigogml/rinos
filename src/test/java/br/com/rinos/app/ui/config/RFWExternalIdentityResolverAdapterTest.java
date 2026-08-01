package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.enums.GoogleIdentityResolutionStatusEnum;
import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;

@DisplayName("Adapter da identidade externa validada pelo RFW")
class RFWExternalIdentityResolverAdapterTest {

  @Test
  void resolve_shouldDiscardClaimsAndMapOpaqueContinuation() {
    GoogleIdentityResolutionFacade facade = mock(GoogleIdentityResolutionFacade.class);
    Instant expiresAt = Instant.parse("2026-07-29T13:00:00Z");
    when(facade.resolve(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleIdentityResolutionResultVO.continuation(
            "opaque-reference",
            "google",
            "person@example.com",
            expiresAt)));
    RFWExternalIdentityResolverAdapter adapter =
        new RFWExternalIdentityResolverAdapter(facade);
    RFWVerifiedExternalIdentityVO identity = new RFWVerifiedExternalIdentityVO(
        "google",
        "subject-1",
        "person@example.com",
        true,
        Map.of(
            "iss", "https://accounts.google.com",
            "profile", "claim-that-must-not-cross-the-api"));

    RFWAuthenticationOutcomeVO outcome =
        adapter.resolve(identity).toCompletableFuture().join();

    ArgumentCaptor<GoogleIdentityResolutionRequestVO> request =
        ArgumentCaptor.forClass(GoogleIdentityResolutionRequestVO.class);
    verify(facade).resolve(request.capture());
    assertThat(request.getValue())
        .extracting(
            GoogleIdentityResolutionRequestVO::providerId,
            GoogleIdentityResolutionRequestVO::issuer,
            GoogleIdentityResolutionRequestVO::subject,
            GoogleIdentityResolutionRequestVO::email,
            GoogleIdentityResolutionRequestVO::emailVerified)
        .containsExactly(
            "google",
            "https://accounts.google.com",
            "subject-1",
            "person@example.com",
            true);
    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.EXTERNAL_REGISTRATION_REQUIRED);
    assertThat(outcome.externalRegistration().registrationReference())
        .isEqualTo("opaque-reference");
    assertThat(outcome.externalRegistration().expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void resolve_shouldMapActiveEmailToSafeReauthenticationMessage() {
    GoogleIdentityResolutionFacade facade = mock(GoogleIdentityResolutionFacade.class);
    when(facade.resolve(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleIdentityResolutionResultVO.of(
            GoogleIdentityResolutionStatusEnum.EXISTING_USER_REAUTHENTICATION_REQUIRED)));
    RFWExternalIdentityResolverAdapter adapter =
        new RFWExternalIdentityResolverAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(
        new RFWVerifiedExternalIdentityVO(
            "google",
            "subject-1",
            "person@example.com",
            true,
            Map.of("iss", "https://accounts.google.com")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.google.existing-user-reauthentication-required");
  }

  @Test
  void resolve_shouldKeepLocalRegistrationAvailable_whenGoogleResolutionIsUnavailable() {
    GoogleIdentityResolutionFacade facade = mock(GoogleIdentityResolutionFacade.class);
    when(facade.resolve(any())).thenReturn(CompletableFuture.completedFuture(
        GoogleIdentityResolutionResultVO.of(
            GoogleIdentityResolutionStatusEnum.UNAVAILABLE)));
    RFWExternalIdentityResolverAdapter adapter =
        new RFWExternalIdentityResolverAdapter(facade);

    RFWAuthenticationOutcomeVO outcome = adapter.resolve(
        new RFWVerifiedExternalIdentityVO(
            "google",
            "subject-1",
            "person@example.com",
            true,
            Map.of("iss", "https://accounts.google.com")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.error().messageKey())
        .isEqualTo("registration.google.unavailable");
  }
}
