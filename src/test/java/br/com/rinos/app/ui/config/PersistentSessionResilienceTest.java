package br.com.rinos.app.ui.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;
import br.com.rinos.app.backend.module.identity.facade.PersistentLoginFacadeImpl;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSessionLifecycleService;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionAccessVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import br.eng.rodrigogml.rfw.authentication.filter.RFWPersistentLoginAuthenticationFilter;
import br.eng.rodrigogml.rfw.authentication.provider.RFWAuthenticationSessionLifecycleProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPersistentLoginProvider;
import br.eng.rodrigogml.rfw.authentication.provider.RFWRememberMeProvider;
import br.eng.rodrigogml.rfw.authentication.service.RFWAuthenticationSessionService;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationSessionValidationVO;
import br.eng.rodrigogml.rfw.executioncontext.RFWExecutionContextRefresher;
import jakarta.servlet.http.Cookie;

/**
 * Exercita a reconstrução local de uma sessão persistente e sua proteção contra fixation.
 *
 * @author Rodrigo Leitão
 */
class PersistentSessionResilienceTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final UUID SESSION = UUID.fromString("2bed0885-277f-4f06-b8ea-eaee3bb145a1");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void persistentCookie_shouldRestoreAuthentication_afterLocalSessionWasLost() throws Exception {
    Fixture fixture = fixture();
    MockHttpServletRequest request = requestWithOldCookie();
    MockHttpServletResponse response = new MockHttpServletResponse();

    fixture.filter().doFilter(request, response, new MockFilterChain());

    assertThat(request.getSession(false)).isNotNull();
    assertThat(securityContext(request).getAuthentication().getPrincipal())
        .isInstanceOfSatisfying(RFWAuthenticatedPrincipalAdapter.class, principal -> {
          assertThat(principal.user().userId()).isEqualTo(42L);
          assertThat(principal.sessionReference()).isEqualTo(SESSION.toString());
        });
    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("RINOS_AUTH=new-selector.new-validator;");
  }

  @Test
  void persistentCookie_shouldChangeExistingSessionId_beforeRestoringAuthentication()
      throws Exception {
    Fixture fixture = fixture();
    MockHttpServletRequest request = requestWithOldCookie();
    String previousSessionId = request.getSession(true).getId();

    fixture.filter().doFilter(
        request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(request.getSession(false).getId()).isNotEqualTo(previousSessionId);
    assertThat(securityContext(request).getAuthentication()).isNotNull();
  }

  private static SecurityContext securityContext(MockHttpServletRequest request) {
    return (SecurityContext) request.getSession(false).getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
  }

  private static MockHttpServletRequest requestWithOldCookie() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("RINOS_AUTH", "old-selector.old-validator"));
    return request;
  }

  @SuppressWarnings("unchecked")
  private static Fixture fixture() {
    AuthSessionService sessionService = mock(AuthSessionService.class);
    when(sessionService.access(
        eq("old-selector.old-validator"),
        eq(true),
        eq(NOW),
        any(UUID.class)))
        .thenReturn(new AuthSessionAccessVO(
            AuthSessionAccessStatusEnum.ROTATED,
            42L,
            "person@example.test",
            SESSION,
            AuthenticationAssuranceEnum.SINGLE_FACTOR,
            NOW,
            NOW.plus(Duration.ofDays(30)),
            NOW.plus(Duration.ofDays(7)),
            "new-selector.new-validator"));
    AuthenticationSessionLifecycleService lifecycleService =
        mock(AuthenticationSessionLifecycleService.class);
    AuthenticationSessionPropertiesConfig properties = properties();
    RFWPersistentLoginProvider adapter = new RFWPersistentLoginProviderAdapter(
        new PersistentLoginFacadeImpl(sessionService, lifecycleService, properties),
        Clock.fixed(NOW, ZoneOffset.UTC));

    RFWAuthenticationSessionLifecycleProvider lifecycleProvider =
        mock(RFWAuthenticationSessionLifecycleProvider.class);
    when(lifecycleProvider.validate(any(), any()))
        .thenReturn(RFWAuthenticationSessionValidationVO.valid());
    ObjectProvider<RFWAuthenticationSessionLifecycleProvider> lifecycle = mock(ObjectProvider.class);
    when(lifecycle.getIfAvailable()).thenReturn(lifecycleProvider);
    ObjectProvider<RFWPersistentLoginProvider> persistent = mock(ObjectProvider.class);
    when(persistent.getIfAvailable()).thenReturn(adapter);
    ObjectProvider<RFWRememberMeProvider> legacy = mock(ObjectProvider.class);
    ObjectProvider<RFWExecutionContextRefresher> refreshers = mock(ObjectProvider.class);
    when(refreshers.orderedStream()).thenAnswer(invocation -> java.util.stream.Stream.empty());
    RFWAuthenticationSessionService rfwSessionService = new RFWAuthenticationSessionService(
        new HttpSessionSecurityContextRepository(),
        new ChangeSessionIdAuthenticationStrategy(),
        lifecycle,
        persistent,
        legacy,
        refreshers);
    return new Fixture(
        new RFWPersistentLoginAuthenticationFilter(adapter, rfwSessionService));
  }

  private static AuthenticationSessionPropertiesConfig properties() {
    return new AuthenticationSessionPropertiesConfig(
        Duration.ofHours(12),
        Duration.ofMinutes(30),
        Duration.ofDays(30),
        Duration.ofDays(7),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        "RINOS_AUTH",
        true,
        "Strict");
  }

  private record Fixture(RFWPersistentLoginAuthenticationFilter filter) {
  }
}
