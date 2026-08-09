package br.com.rinos.app.backend.module.identity.facade;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import br.com.rinos.app.api.enums.PersistentLoginStatusEnum;
import br.com.rinos.app.api.facade.PersistentLoginFacade;
import br.com.rinos.app.api.vo.PersistentLoginResultVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionAccessStatusEnum;
import br.com.rinos.app.backend.module.identity.service.AuthenticationSessionLifecycleService;
import br.com.rinos.app.backend.module.identity.service.AuthSessionService;
import br.com.rinos.app.backend.module.identity.vo.AuthSessionAccessVO;
import br.com.rinos.app.backend.module.identity.vo.IssuedPersistentLoginVO;
import br.com.rinos.app.config.AuthenticationSessionPropertiesConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Mantém cookie e sessão global na mesma fronteira sem expor o segredo ao RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Service
@Lazy
public class PersistentLoginFacadeImpl implements PersistentLoginFacade {

  private final AuthSessionService sessionService;
  private final AuthenticationSessionLifecycleService lifecycleService;
  private final AuthenticationSessionPropertiesConfig properties;

  /** Cria a fachada com a autoridade persistente e a política fixa do cookie. */
  public PersistentLoginFacadeImpl(
      AuthSessionService sessionService,
      AuthenticationSessionLifecycleService lifecycleService,
      AuthenticationSessionPropertiesConfig properties) {
    this.sessionService = sessionService;
    this.lifecycleService = lifecycleService;
    this.properties = properties;
  }

  @Override
  public void create(
      String sessionReference,
      HttpServletResponse response,
      Instant occurredAt) {
    Objects.requireNonNull(response, "response must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference = requireReference(sessionReference);
    IssuedPersistentLoginVO issued = sessionService.issuePersistentCredential(
        reference, occurredAt);
    write(response, issued.cookieValue(), remaining(occurredAt, issued.absoluteExpiresAt()));
  }

  @Override
  public PersistentLoginResultVO resolveAndRotate(
      HttpServletRequest request,
      HttpServletResponse response,
      Instant occurredAt) {
    Objects.requireNonNull(request, "request must not be null");
    Objects.requireNonNull(response, "response must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    CookieLookup lookup = read(request);
    if (lookup == CookieLookup.ABSENT) {
      return terminal(PersistentLoginStatusEnum.ABSENT);
    }
    if (lookup == CookieLookup.INVALID) {
      return terminal(PersistentLoginStatusEnum.INVALID);
    }
    AuthSessionAccessVO access = sessionService.access(
        lookup.value(), true, occurredAt, UUID.randomUUID());
    if (access.status() == AuthSessionAccessStatusEnum.ROTATED) {
      write(response, access.rotatedCookieValue(), remaining(occurredAt, access.absoluteExpiresAt()));
      return new PersistentLoginResultVO(
          PersistentLoginStatusEnum.RESTORED,
          new RinosUserPrincipalVO(access.userId(), access.userEmail()),
          access.publicReference().toString());
    }
    return terminal(switch (access.status()) {
      case EXPIRED -> PersistentLoginStatusEnum.EXPIRED;
      case REVOKED -> PersistentLoginStatusEnum.REVOKED;
      case BLOCKED -> PersistentLoginStatusEnum.BLOCKED;
      case REPLAY_DETECTED -> PersistentLoginStatusEnum.REPLAY_DETECTED;
      case REJECTED, ACTIVE, ROTATED -> PersistentLoginStatusEnum.INVALID;
    });
  }

  @Override
  public void revoke(String sessionReference, Instant occurredAt) {
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    UUID reference = parseReference(sessionReference);
    if (reference != null) {
      lifecycleService.close(reference, occurredAt);
    }
  }

  @Override
  public void clear(HttpServletResponse response) {
    Objects.requireNonNull(response, "response must not be null");
    write(response, "", Duration.ZERO);
  }

  private CookieLookup read(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return CookieLookup.ABSENT;
    }
    String[] values = Arrays.stream(cookies)
        .filter(cookie -> properties.cookieName().equals(cookie.getName()))
        .map(Cookie::getValue)
        .toArray(String[]::new);
    if (values.length == 0) {
      return CookieLookup.absent();
    }
    if (values.length != 1 || values[0] == null || values[0].isBlank()) {
      return CookieLookup.invalid();
    }
    return new CookieLookup(values[0]);
  }

  private void write(HttpServletResponse response, String value, Duration maxAge) {
    ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite(properties.cookieSameSite())
        .path("/")
        .maxAge(maxAge)
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private static Duration remaining(Instant occurredAt, Instant expiresAt) {
    Duration duration = Duration.between(occurredAt, expiresAt);
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalStateException("Persistent login is already expired");
    }
    return duration;
  }

  private static PersistentLoginResultVO terminal(PersistentLoginStatusEnum status) {
    return new PersistentLoginResultVO(status, null, null);
  }

  private static UUID requireReference(String value) {
    UUID reference = parseReference(value);
    if (reference == null) {
      throw new IllegalArgumentException("sessionReference is invalid");
    }
    return reference;
  }

  private static UUID parseReference(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException invalidReference) {
      return null;
    }
  }

  private record CookieLookup(String value) {
    private static final CookieLookup ABSENT = new CookieLookup(null);
    private static final CookieLookup INVALID = new CookieLookup(null);

    private static CookieLookup absent() {
      return ABSENT;
    }

    private static CookieLookup invalid() {
      return INVALID;
    }
  }
}
