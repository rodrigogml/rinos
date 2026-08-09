package br.com.rinos.app.ui.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.AuthenticationSessionPreparationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationSessionLifecycleStatusEnum;
import br.com.rinos.app.api.facade.AuthenticationSessionLifecycleFacade;
import br.com.rinos.app.api.vo.AuthenticationSessionLifecycleResultVO;
import br.com.rinos.app.api.vo.RinosAuthenticationCompletionVO;
import br.com.rinos.app.api.vo.RinosUserPrincipalVO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWAuthenticationSessionLifecycleProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationSessionPreparationVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationSessionValidationVO;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Liga o lifecycle local do RFW à autoridade global de sessão do Rinos.
 *
 * <p>A continuação do fluxo é removida de {@code details} antes da autenticação preparada
 * ser devolvida. Somente identidade, authorities e referência não autenticadora permanecem.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
public class RFWAuthenticationSessionLifecycleProviderAdapter
    implements RFWAuthenticationSessionLifecycleProvider {

  private final AuthenticationSessionLifecycleFacade facade;
  private final RFWRemoteAddressProvider remoteAddressProvider;
  private final Clock clock;

  /** Cria o adapter com relógio UTC e origem validada pela integração RFW. */
  @Autowired
  public RFWAuthenticationSessionLifecycleProviderAdapter(
      @Lazy AuthenticationSessionLifecycleFacade facade,
      RFWRemoteAddressProvider remoteAddressProvider) {
    this(facade, remoteAddressProvider, Clock.systemUTC());
  }

  RFWAuthenticationSessionLifecycleProviderAdapter(
      AuthenticationSessionLifecycleFacade facade,
      RFWRemoteAddressProvider remoteAddressProvider,
      Clock clock) {
    this.facade = java.util.Objects.requireNonNull(facade, "facade must not be null");
    this.remoteAddressProvider = java.util.Objects.requireNonNull(
        remoteAddressProvider, "remoteAddressProvider must not be null");
    this.clock = java.util.Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public RFWAuthenticationSessionPreparationVO prepare(
      HttpServletRequest request,
      Authentication authentication,
      boolean persistent) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof RinosUserPrincipalVO user)
        || !(authentication.getDetails() instanceof RinosAuthenticationCompletionVO completion)) {
      throw new IllegalStateException("Authentication completion is missing");
    }
    AuthenticationSessionLifecycleResultVO result = facade.prepare(
        new AuthenticationSessionPreparationRequestDTO(
            completion.flowReference(),
            completion.purpose(),
            user.userId(),
            persistent,
            remoteAddressProvider.resolve(request),
            boundedUserAgent(request.getHeader("User-Agent")),
            clock.instant()));
    if ((result.status() != AuthenticationSessionLifecycleStatusEnum.PREPARED
        && result.status() != AuthenticationSessionLifecycleStatusEnum.ACTIVE)
        || result.principal() == null
        || result.principal().userId() != user.userId()
        || result.persistent() != persistent
        || result.sessionReference() == null) {
      throw new IllegalStateException("Authentication session could not be prepared");
    }
    UsernamePasswordAuthenticationToken prepared =
        UsernamePasswordAuthenticationToken.authenticated(
            new RFWAuthenticatedPrincipalAdapter(result.principal(), result.sessionReference()),
            null,
            authentication.getAuthorities());
    prepared.setDetails(null);
    return new RFWAuthenticationSessionPreparationVO(prepared);
  }

  @Override
  public void publish(RFWAuthenticationSessionPreparationVO preparation) {
    AuthenticationSessionLifecycleResultVO result = facade.publish(
        sessionReference(preparation.authentication()), clock.instant());
    if (result.status() != AuthenticationSessionLifecycleStatusEnum.ACTIVE) {
      throw new IllegalStateException("Authentication session could not be published");
    }
  }

  @Override
  public RFWAuthenticationSessionValidationVO validate(
      HttpServletRequest request,
      Authentication authentication) {
    String reference = optionalSessionReference(authentication);
    if (reference == null) {
      return RFWAuthenticationSessionValidationVO.invalid();
    }
    try {
      return switch (facade.validate(reference, clock.instant()).status()) {
        case ACTIVE -> RFWAuthenticationSessionValidationVO.valid();
        case EXPIRED -> RFWAuthenticationSessionValidationVO.expired();
        case REVOKED -> RFWAuthenticationSessionValidationVO.revoked();
        case BLOCKED -> RFWAuthenticationSessionValidationVO.blocked();
        case UNAVAILABLE -> RFWAuthenticationSessionValidationVO.unavailable();
        case PREPARED, INVALID -> RFWAuthenticationSessionValidationVO.invalid();
      };
    } catch (RuntimeException unavailable) {
      return RFWAuthenticationSessionValidationVO.unavailable();
    }
  }

  @Override
  public void abort(RFWAuthenticationSessionPreparationVO preparation) {
    facade.abort(sessionReference(preparation.authentication()), clock.instant());
  }

  @Override
  public void close(HttpServletRequest request, Authentication authentication) {
    String reference = optionalSessionReference(authentication);
    if (reference != null) {
      facade.close(reference, clock.instant());
    }
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

  private static String boundedUserAgent(String userAgent) {
    return userAgent == null || userAgent.length() <= 2048
        ? userAgent : userAgent.substring(0, 2048);
  }
}
