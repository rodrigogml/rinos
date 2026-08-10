package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import br.com.rinos.app.api.dto.AuthenticationSessionPreparationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.AuthenticationSessionLifecycleStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationSessionLifecycleFacade;
import br.com.rinos.app.api.vo.AuthenticationSessionLifecycleResultVO;
import br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationSessionStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationSessionPreparationVO;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;
import jakarta.servlet.http.HttpServletRequest;

@DisplayName("Adapter RFW do lifecycle da sessão Rinos")
class RFWAuthenticationSessionLifecycleProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final String SESSION_REFERENCE =
      "bf6884a6-edfe-405c-b685-57b299ad7e62";

  @Test
  void prepare_shouldPersistBeforeReturningSanitizedAuthentication() {
    AuthenticationSessionLifecycleFacade facade = mock(
        AuthenticationSessionLifecycleFacade.class);
    RFWRemoteAddressProvider remote = mock(RFWRemoteAddressProvider.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(remote.resolve(request)).thenReturn("203.0.113.10");
    when(request.getHeader("User-Agent")).thenReturn("Browser/1.0");
    when(facade.prepare(any())).thenReturn(result(
        AuthenticationSessionLifecycleStatusEnum.PREPARED));
    RFWAuthenticationSessionLifecycleProviderAdapter adapter = adapter(facade, remote);
    Authentication authentication = pendingAuthentication();

    RFWAuthenticationSessionPreparationVO preparation = adapter.prepare(
        request, authentication, true);

    assertThat(preparation.authentication().isAuthenticated()).isTrue();
    assertThat(preparation.authentication().getPrincipal())
        .isEqualTo(new RFWAuthenticatedPrincipalAdapter(
            new RinosUserPrincipalVO(41L, "person@example.test"), SESSION_REFERENCE));
    assertThat(preparation.authentication().getDetails()).isNull();
    assertThat(preparation.authentication().getName()).isEqualTo("person@example.test");
    assertThat(preparation.authentication().getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_USER");
    ArgumentCaptor<AuthenticationSessionPreparationRequestDTO> captured =
        ArgumentCaptor.forClass(AuthenticationSessionPreparationRequestDTO.class);
    verify(facade).prepare(captured.capture());
    assertThat(captured.getValue().flowReference()).isEqualTo("flow-reference");
    assertThat(captured.getValue().expectedUserId()).isEqualTo(41L);
    assertThat(captured.getValue().persistent()).isTrue();
    assertThat(captured.getValue().canonicalOrigin()).isEqualTo("203.0.113.10");
    assertThat(captured.getValue().occurredAt()).isEqualTo(NOW);
  }

  @Test
  void publish_shouldRequireActiveBackendSession() {
    AuthenticationSessionLifecycleFacade facade = mock(
        AuthenticationSessionLifecycleFacade.class);
    when(facade.publish(SESSION_REFERENCE, NOW)).thenReturn(result(
        AuthenticationSessionLifecycleStatusEnum.REVOKED));
    RFWAuthenticationSessionLifecycleProviderAdapter adapter = adapter(
        facade, mock(RFWRemoteAddressProvider.class));
    RFWAuthenticationSessionPreparationVO preparation = preparedAuthentication();

    assertThatThrownBy(() -> adapter.publish(preparation))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void validate_shouldMapStateAndFailClosedAsUnavailable() {
    AuthenticationSessionLifecycleFacade facade = mock(
        AuthenticationSessionLifecycleFacade.class);
    when(facade.validate(SESSION_REFERENCE, NOW))
        .thenReturn(result(AuthenticationSessionLifecycleStatusEnum.BLOCKED))
        .thenThrow(new IllegalStateException("database unavailable"));
    RFWAuthenticationSessionLifecycleProviderAdapter adapter = adapter(
        facade, mock(RFWRemoteAddressProvider.class));
    Authentication authentication = preparedAuthentication().authentication();

    assertThat(adapter.validate(mock(HttpServletRequest.class), authentication).status())
        .isEqualTo(RFWAuthenticationSessionStatusEnum.BLOCKED);
    assertThat(adapter.validate(mock(HttpServletRequest.class), authentication).status())
        .isEqualTo(RFWAuthenticationSessionStatusEnum.UNAVAILABLE);
  }

  @Test
  void abortAndClose_shouldDelegateTheOpaqueReference() {
    AuthenticationSessionLifecycleFacade facade = mock(
        AuthenticationSessionLifecycleFacade.class);
    RFWAuthenticationSessionLifecycleProviderAdapter adapter = adapter(
        facade, mock(RFWRemoteAddressProvider.class));
    RFWAuthenticationSessionPreparationVO preparation = preparedAuthentication();

    adapter.abort(preparation);
    adapter.close(mock(HttpServletRequest.class), preparation.authentication());

    verify(facade).abort(SESSION_REFERENCE, NOW);
    verify(facade).close(SESSION_REFERENCE, NOW);
  }

  private static RFWAuthenticationSessionLifecycleProviderAdapter adapter(
      AuthenticationSessionLifecycleFacade facade,
      RFWRemoteAddressProvider remote) {
    return new RFWAuthenticationSessionLifecycleProviderAdapter(
        facade, remote, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Authentication pendingAuthentication() {
    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            new RinosUserPrincipalVO(41L, "person@example.test"),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    authentication.setDetails(new RinosAuthenticationCompletionVO(
        "flow-reference", AuthenticationFlowPurposeEnum.SIGN_IN));
    return authentication;
  }

  private static RFWAuthenticationSessionPreparationVO preparedAuthentication() {
    return new RFWAuthenticationSessionPreparationVO(
        UsernamePasswordAuthenticationToken.authenticated(
            new RFWAuthenticatedPrincipalAdapter(
                new RinosUserPrincipalVO(41L, "person@example.test"), SESSION_REFERENCE),
            null,
            List.of()));
  }

  private static AuthenticationSessionLifecycleResultVO result(
      AuthenticationSessionLifecycleStatusEnum status) {
    boolean usable = status == AuthenticationSessionLifecycleStatusEnum.PREPARED
        || status == AuthenticationSessionLifecycleStatusEnum.ACTIVE;
    return new AuthenticationSessionLifecycleResultVO(
        status,
        usable ? SESSION_REFERENCE : null,
        usable ? new RinosUserPrincipalVO(41L, "person@example.test") : null,
        usable,
        usable ? NOW.plusSeconds(3600) : null);
  }
}
