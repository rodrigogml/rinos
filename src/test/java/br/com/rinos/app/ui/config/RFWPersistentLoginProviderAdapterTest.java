package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import br.com.rinos.app.api.enums.PersistentLoginStatusEnum;
import br.com.rinos.app.api.facade.PersistentLoginFacade;
import br.com.rinos.app.api.vo.PersistentLoginResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWPersistentLoginStatusEnum;
import br.eng.rodrigogml.rfw.authentication.vo.RFWPersistentLoginOutcomeVO;

/**
 * Verifica a tradução entre o provider RFW e o contrato seguro do Rinos.
 *
 * @author Rodrigo Leitão
 */
class RFWPersistentLoginProviderAdapterTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final String SESSION = "2bed0885-277f-4f06-b8ea-eaee3bb145a1";

  @Test
  void createAndRevoke_shouldUseReferenceFromAuthenticatedPrincipal() {
    PersistentLoginFacade facade = mock(PersistentLoginFacade.class);
    RFWPersistentLoginProviderAdapter adapter = adapter(facade);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Authentication authentication = authentication();

    adapter.create(request, response, authentication);
    adapter.revoke(request, response, authentication);
    adapter.clear(request, response);

    verify(facade).create(SESSION, response, NOW);
    verify(facade).revoke(SESSION, NOW);
    verify(facade).clear(response);
  }

  @Test
  void resolveAndRotate_shouldCreateMinimalAuthenticatedPrincipal() {
    PersistentLoginFacade facade = mock(PersistentLoginFacade.class);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(facade.resolveAndRotate(request, response, NOW)).thenReturn(
        new PersistentLoginResultVO(
            PersistentLoginStatusEnum.RESTORED,
            new RinosUserPrincipalVO(42L, "person@example.test"),
            SESSION));

    RFWPersistentLoginOutcomeVO outcome = adapter(facade).resolveAndRotate(request, response);

    assertThat(outcome.status()).isEqualTo(RFWPersistentLoginStatusEnum.RESTORED);
    assertThat(outcome.authentication().getPrincipal())
        .isInstanceOfSatisfying(RFWAuthenticatedPrincipalAdapter.class,
            principal -> assertThat(principal.sessionReference()).isEqualTo(SESSION));
    assertThat(outcome.authentication().getAuthorities()).isEmpty();
  }

  @Test
  void resolveAndRotate_shouldFailClosedButPreserveCookieOnFacadeFailure() {
    PersistentLoginFacade facade = mock(PersistentLoginFacade.class);
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(facade.resolveAndRotate(request, response, NOW))
        .thenThrow(new IllegalStateException("database unavailable"));

    RFWPersistentLoginOutcomeVO outcome = adapter(facade).resolveAndRotate(request, response);

    assertThat(outcome.status()).isEqualTo(RFWPersistentLoginStatusEnum.UNAVAILABLE);
    assertThat(response.getHeader("Set-Cookie")).isNull();
  }

  private static RFWPersistentLoginProviderAdapter adapter(PersistentLoginFacade facade) {
    return new RFWPersistentLoginProviderAdapter(
        facade, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static Authentication authentication() {
    return UsernamePasswordAuthenticationToken.authenticated(
        new RFWAuthenticatedPrincipalAdapter(
            new RinosUserPrincipalVO(42L, "person@example.test"), SESSION),
        null,
        List.of());
  }
}
