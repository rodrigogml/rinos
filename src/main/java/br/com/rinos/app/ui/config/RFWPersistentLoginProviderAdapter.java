package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.facade.PersistentLoginFacade;
import br.com.rinos.app.api.vo.PersistentLoginResultVO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPersistentLoginProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWPersistentLoginOutcomeVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Liga o lifecycle persistente do RFW à fachada HTTP segura do Rinos.
 *
 * <p>O adapter nunca recebe o valor do cookie como DTO nem o conserva no estado da interface.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
public class RFWPersistentLoginProviderAdapter implements RFWPersistentLoginProvider {

  private final PersistentLoginFacade facade;
  private final Clock clock;

  /** Cria o adapter com relógio UTC. */
  @Autowired
  public RFWPersistentLoginProviderAdapter(@Lazy PersistentLoginFacade facade) {
    this(facade, Clock.systemUTC());
  }

  RFWPersistentLoginProviderAdapter(PersistentLoginFacade facade, Clock clock) {
    this.facade = Objects.requireNonNull(facade, "facade must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public void create(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) {
    facade.create(sessionReference(authentication), response, clock.instant());
  }

  @Override
  public RFWPersistentLoginOutcomeVO resolveAndRotate(
      HttpServletRequest request,
      HttpServletResponse response) {
    PersistentLoginResultVO result;
    try {
      result = facade.resolveAndRotate(request, response, clock.instant());
    } catch (RuntimeException unavailable) {
      return RFWPersistentLoginOutcomeVO.unavailable();
    }
    return switch (result.status()) {
      case ABSENT -> RFWPersistentLoginOutcomeVO.absent();
      case INVALID -> RFWPersistentLoginOutcomeVO.invalid();
      case EXPIRED -> RFWPersistentLoginOutcomeVO.expired();
      case REVOKED -> RFWPersistentLoginOutcomeVO.revoked();
      case BLOCKED -> RFWPersistentLoginOutcomeVO.blocked();
      case REPLAY_DETECTED -> RFWPersistentLoginOutcomeVO.replayDetected();
      case RESTORED -> RFWPersistentLoginOutcomeVO.restored(authentication(result));
    };
  }

  @Override
  public void revoke(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication) {
    String reference = optionalSessionReference(authentication);
    if (reference != null) {
      facade.revoke(reference, clock.instant());
    }
  }

  @Override
  public void clear(HttpServletRequest request, HttpServletResponse response) {
    facade.clear(response);
  }

  private static Authentication authentication(PersistentLoginResultVO result) {
    if (result.principal() == null || result.sessionReference() == null) {
      throw new IllegalStateException("Restored persistent login is incomplete");
    }
    return UsernamePasswordAuthenticationToken.authenticated(
        new RFWAuthenticatedPrincipalAdapter(result.principal(), result.sessionReference()),
        null,
        List.of());
  }

  private static String sessionReference(Authentication authentication) {
    String reference = optionalSessionReference(authentication);
    if (reference == null) {
      throw new IllegalArgumentException("Authentication does not contain a session reference");
    }
    return reference;
  }

  private static String optionalSessionReference(Authentication authentication) {
    return authentication != null
        && authentication.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal
        ? principal.sessionReference() : null;
  }
}
