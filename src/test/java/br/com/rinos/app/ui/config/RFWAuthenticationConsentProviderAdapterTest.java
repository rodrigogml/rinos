package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.rinos.app.api.dto.AuthenticationConsentRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationMethodEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationConsentFacade;
import br.com.rinos.app.api.vo.AuthenticationMethodEvidenceVO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWAuthenticationConsentRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;

@DisplayName("Provider RFW do aceite legal pós-autenticação")
class RFWAuthenticationConsentProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

  @Test
  void completeAuthenticationConsent_shouldResumeThroughOfficialLifecycleWithoutAuthorities() {
    AuthenticationConsentFacade facade = mock(AuthenticationConsentFacade.class);
    when(facade.complete(any())).thenReturn(new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.READY,
        "opaque-flow",
        new RinosUserPrincipalVO(41L, "person@example.test"),
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(new AuthenticationMethodEvidenceVO(
            AuthenticationMethodEnum.PASSWORD, NOW, null)),
        Set.of(),
        false,
        NOW.plusSeconds(300),
        UUID.fromString("590cd2c5-5b5d-45e6-bfbf-887033f407f0")));
    RFWAuthenticationConsentProviderAdapter adapter =
        new RFWAuthenticationConsentProviderAdapter(
            facade,
            new RFWAuthenticationOutcomeAdapter(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    var outcome = adapter.completeAuthenticationConsent(
        new RFWAuthenticationConsentRequestDTO("opaque-flow", List.of("11", "12")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.AUTHENTICATED);
    assertThat(outcome.authentication().getAuthorities()).isEmpty();
    ArgumentCaptor<AuthenticationConsentRequestDTO> request =
        ArgumentCaptor.forClass(AuthenticationConsentRequestDTO.class);
    verify(facade).complete(request.capture());
    assertThat(request.getValue().continuationReference()).isEqualTo("opaque-flow");
    assertThat(request.getValue().acceptedLegalDocumentIds()).containsExactly("11", "12");
    assertThat(request.getValue().occurredAt()).isEqualTo(NOW);
  }

  @Test
  void cancelAuthenticationConsent_shouldCancelOpaqueContinuation() {
    AuthenticationConsentFacade facade = mock(AuthenticationConsentFacade.class);
    RFWAuthenticationConsentProviderAdapter adapter =
        new RFWAuthenticationConsentProviderAdapter(
            facade,
            new RFWAuthenticationOutcomeAdapter(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    adapter.cancelAuthenticationConsent("opaque-flow").toCompletableFuture().join();

    verify(facade).cancel("opaque-flow", NOW);
  }

  @Test
  void completeAuthenticationConsent_shouldPresentNewRequiredVersionWithoutAuthentication() {
    AuthenticationConsentFacade facade = mock(AuthenticationConsentFacade.class);
    when(facade.complete(any())).thenReturn(new AuthenticationOrchestrationResultVO(
        AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
        "opaque-flow",
        null,
        AuthenticationAssuranceEnum.SINGLE_FACTOR,
        Set.of(),
        List.of(new AuthenticationMethodEvidenceVO(
            AuthenticationMethodEnum.PASSWORD, NOW, null)),
        Set.of("13"),
        false,
        NOW.plusSeconds(300),
        UUID.fromString("590cd2c5-5b5d-45e6-bfbf-887033f407f0")));
    RFWAuthenticationConsentProviderAdapter adapter =
        new RFWAuthenticationConsentProviderAdapter(
            facade,
            new RFWAuthenticationOutcomeAdapter(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    var outcome = adapter.completeAuthenticationConsent(
        new RFWAuthenticationConsentRequestDTO("opaque-flow", List.of("11", "12")))
        .toCompletableFuture()
        .join();

    assertThat(outcome.status()).isEqualTo(
        RFWAccessStatusEnum.AUTHENTICATION_CONSENT_REQUIRED);
    assertThat(outcome.authentication()).isNull();
    assertThat(outcome.authenticationConsent().legalDocumentIds()).containsExactly("13");
  }
}
