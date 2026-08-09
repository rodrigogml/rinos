package br.com.rinos.app.backend.module.identity.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import br.com.rinos.app.api.enums.PersistentLoginStatusEnum;
import br.com.rinos.app.api.vo.PersistentLoginResultVO;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSessionLifecycleService;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionAccessVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedPersistentLoginVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import jakarta.servlet.http.Cookie;

/**
 * Verifica a fronteira que mantém a credencial fora dos VOs públicos.
 *
 * @author Rodrigo Leitão
 */
class PersistentLoginFacadeImplTest {

  private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
  private static final UUID SESSION = UUID.fromString("2bed0885-277f-4f06-b8ea-eaee3bb145a1");
  private AuthSessionService sessionService;
  private AuthenticationSessionLifecycleService lifecycleService;
  private PersistentLoginFacadeImpl facade;

  @BeforeEach
  void setUp() {
    sessionService = mock(AuthSessionService.class);
    lifecycleService = mock(AuthenticationSessionLifecycleService.class);
    facade = new PersistentLoginFacadeImpl(
        sessionService,
        lifecycleService,
        new AuthenticationSessionPropertiesConfig(
            Duration.ofHours(12),
            Duration.ofMinutes(30),
            Duration.ofDays(30),
            Duration.ofDays(7),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            "RINOS_AUTH",
            true,
            "Strict"));
  }

  @Test
  void create_shouldWriteHardenedCookie_withoutExposingCredentialInResult() {
    when(sessionService.issuePersistentCredential(SESSION, NOW)).thenReturn(
        new IssuedPersistentLoginVO("selector.validator", SESSION, NOW.plus(Duration.ofDays(30))));
    MockHttpServletResponse response = new MockHttpServletResponse();

    facade.create(SESSION.toString(), response, NOW);

    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("RINOS_AUTH=selector.validator;")
        .contains("Path=/", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Strict");
  }

  @Test
  void resolveAndRotate_shouldPublishNewCookieAndSafePrincipal() {
    when(sessionService.access(
        org.mockito.ArgumentMatchers.eq("old-selector.old-validator"),
        org.mockito.ArgumentMatchers.eq(true),
        org.mockito.ArgumentMatchers.eq(NOW),
        org.mockito.ArgumentMatchers.any(UUID.class)))
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
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("RINOS_AUTH", "old-selector.old-validator"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    PersistentLoginResultVO result = facade.resolveAndRotate(request, response, NOW);

    assertThat(result.status()).isEqualTo(PersistentLoginStatusEnum.RESTORED);
    assertThat(result.principal().userId()).isEqualTo(42L);
    assertThat(result.sessionReference()).isEqualTo(SESSION.toString());
    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("RINOS_AUTH=new-selector.new-validator;")
        .contains("Secure", "HttpOnly", "SameSite=Strict");
  }

  @Test
  void resolveAndRotate_shouldRejectDuplicateCookies_withoutTouchingPersistence() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("RINOS_AUTH", "first.value"),
        new Cookie("RINOS_AUTH", "second.value"));

    PersistentLoginResultVO result = facade.resolveAndRotate(
        request, new MockHttpServletResponse(), NOW);

    assertThat(result.status()).isEqualTo(PersistentLoginStatusEnum.INVALID);
    verify(sessionService, never()).access(
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.anyBoolean(),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());
  }

  @Test
  void revokeAndClear_shouldCloseGlobalSessionAndExpireBrowserCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    facade.revoke(SESSION.toString(), NOW);
    facade.clear(response);

    verify(lifecycleService).close(SESSION, NOW);
    assertThat(response.getHeader("Set-Cookie"))
        .startsWith("RINOS_AUTH=;")
        .contains("Path=/", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict");
  }
}
