package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;

import br.com.rinos.app.api.dto.PasskeyAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.api.enums.AuthenticationOrchestrationStatusEnum;
import br.com.rinos.app.api.facade.PasskeyAuthenticationFacade;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAccessStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWValidatedPasskeyAuthenticationVO;

@DisplayName("Adapter da autenticação WebAuthn validada")
class RFWPasskeyAuthenticationProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

  @Test
  void authenticate_shouldReduceConcreteSpringAuthenticationAndPreserveLegalGate() {
    byte[] handle = new byte[32];
    handle[0] = 12;
    var principal = ImmutablePublicKeyCredentialUserEntity.builder()
        .id(new Bytes(handle))
        .name("person@example.test")
        .displayName("person@example.test")
        .build();
    WebAuthnAuthentication authentication = new WebAuthnAuthentication(
        principal,
        List.of(FactorGrantedAuthority.fromAuthority(
            FactorGrantedAuthority.WEBAUTHN_AUTHORITY)));
    PasskeyAuthenticationFacade facade = mock(PasskeyAuthenticationFacade.class);
    when(facade.authenticate(any())).thenReturn(CompletableFuture.completedFuture(
        new AuthenticationOrchestrationResultVO(
            AuthenticationOrchestrationStatusEnum.LEGAL_CONSENT_REQUIRED,
            "opaque-flow",
            null,
            AuthenticationAssuranceEnum.PHISHING_RESISTANT,
            Set.of(),
            List.of(),
            Set.of("11"),
            false,
            NOW.plusSeconds(300),
            UUID.randomUUID())));
    RFWPasskeyAuthenticationProviderAdapter adapter =
        new RFWPasskeyAuthenticationProviderAdapter(
            facade, new RFWAuthenticationOutcomeAdapter());

    var outcome = adapter.authenticate(
        new RFWValidatedPasskeyAuthenticationVO(authentication, NOW))
        .toCompletableFuture().join();

    ArgumentCaptor<PasskeyAuthenticationRequestDTO> captor =
        ArgumentCaptor.forClass(PasskeyAuthenticationRequestDTO.class);
    verify(facade).authenticate(captor.capture());
    assertThat(captor.getValue().userHandle()).containsExactly(handle);
    assertThat(captor.getValue().validatedAt()).isEqualTo(NOW);
    assertThat(outcome.status()).isEqualTo(
        RFWAccessStatusEnum.AUTHENTICATION_CONSENT_REQUIRED);
    assertThat(outcome.authentication()).isNull();
  }

  @Test
  void authenticate_shouldRejectGenericAuthenticatedToken_withoutCallingFacade() {
    PasskeyAuthenticationFacade facade = mock(PasskeyAuthenticationFacade.class);
    RFWPasskeyAuthenticationProviderAdapter adapter =
        new RFWPasskeyAuthenticationProviderAdapter(
            facade, new RFWAuthenticationOutcomeAdapter());
    var forged = UsernamePasswordAuthenticationToken.authenticated(
        "person@example.test", null, List.of());

    var outcome = adapter.authenticate(new RFWValidatedPasskeyAuthenticationVO(forged, NOW))
        .toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    assertThat(outcome.error().messageKey()).isEqualTo("authentication.credentials.invalid");
    verify(facade, never()).authenticate(any());
  }

  @Test
  void authenticate_shouldRejectWebAuthnToken_withoutFactorAuthority() {
    var principal = ImmutablePublicKeyCredentialUserEntity.builder()
        .id(new Bytes(new byte[32]))
        .name("person@example.test")
        .displayName("person@example.test")
        .build();
    PasskeyAuthenticationFacade facade = mock(PasskeyAuthenticationFacade.class);
    RFWPasskeyAuthenticationProviderAdapter adapter =
        new RFWPasskeyAuthenticationProviderAdapter(
            facade, new RFWAuthenticationOutcomeAdapter());

    var outcome = adapter.authenticate(new RFWValidatedPasskeyAuthenticationVO(
        new WebAuthnAuthentication(principal, List.of()), NOW)).toCompletableFuture().join();

    assertThat(outcome.status()).isEqualTo(RFWAccessStatusEnum.REJECTED);
    verify(facade, never()).authenticate(any());
  }
}
