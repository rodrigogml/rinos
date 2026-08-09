package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.ReauthenticationBeginRequestDTO;
import br.com.rinos.app.api.dto.ReauthenticationVerificationRequestDTO;
import br.com.rinos.app.api.facade.ReauthenticationFacade;
import br.com.rinos.app.api.vo.ReauthenticationResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWReauthenticationBeginRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWReauthenticationVerificationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWReauthenticationChallengeProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWReauthenticationChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWReauthenticationOutcomeVO;

/**
 * Adapta o protocolo tipado de reautenticação ao contexto autenticado do Rinos.
 *
 * <p>Identidade e sessão sempre são derivadas do {@link SecurityContextHolder}; nenhum dado do
 * request do componente pode selecionar outra sessão. O provider apenas confirma garantia e não
 * cria autenticação, sessão ou authority.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
public class RFWReauthenticationChallengeProviderAdapter
    implements RFWReauthenticationChallengeProvider {

  private final ReauthenticationFacade facade;
  private final Clock clock;

  /** Cria o provider com relógio UTC. */
  @Autowired
  public RFWReauthenticationChallengeProviderAdapter(
      @Lazy ReauthenticationFacade facade) {
    this(facade, Clock.systemUTC());
  }

  RFWReauthenticationChallengeProviderAdapter(
      ReauthenticationFacade facade,
      Clock clock) {
    this.facade = facade;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWReauthenticationOutcomeVO> begin(
      RFWReauthenticationBeginRequestDTO request) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (request == null || principal == null) {
      return completed(RFWReauthenticationOutcomeVO.accessDenied(errorAccessDenied()));
    }
    try {
      return completed(map(facade.begin(new ReauthenticationBeginRequestDTO(
          principal.user().userId(),
          principal.sessionReference(),
          request.operationId(),
          clock.instant()))));
    } catch (RuntimeException unavailable) {
      return completed(RFWReauthenticationOutcomeVO.unavailable(errorUnavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWReauthenticationOutcomeVO> verify(
      RFWReauthenticationVerificationRequestDTO request) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (request == null || principal == null) {
      return completed(RFWReauthenticationOutcomeVO.accessDenied(errorAccessDenied()));
    }
    try {
      return completed(map(facade.verify(new ReauthenticationVerificationRequestDTO(
          principal.user().userId(),
          principal.sessionReference(),
          request.challengeReference(),
          br.com.rinos.app.api.enums.AuthenticationMethodEnum.valueOf(request.method().name()),
          request.proof(),
          clock.instant()))));
    } catch (RuntimeException unavailable) {
      return completed(RFWReauthenticationOutcomeVO.unavailable(errorUnavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<Void> cancel(String challengeReference) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal != null) {
      try {
        facade.cancel(
            principal.user().userId(),
            principal.sessionReference(),
            challengeReference,
            clock.instant());
      } catch (RuntimeException unavailable) {
        // A referência continua curta e expira sem executar a operação original.
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  private static RFWReauthenticationOutcomeVO map(ReauthenticationResultVO result) {
    if (result == null) {
      return RFWReauthenticationOutcomeVO.unavailable(errorUnavailable());
    }
    return switch (result.status()) {
      case ALREADY_RECENT -> RFWReauthenticationOutcomeVO.alreadyRecent();
      case CHALLENGE_REQUIRED -> RFWReauthenticationOutcomeVO.challengeRequired(
          new RFWReauthenticationChallengeVO(
              result.challengeReference(),
              result.operationLabelKey(),
              result.expiresAt(),
              methods(result)));
      case COMPLETED -> RFWReauthenticationOutcomeVO.completed();
      case REJECTED -> RFWReauthenticationOutcomeVO.rejected(errorRejected());
      case EXPIRED -> RFWReauthenticationOutcomeVO.expired(errorExpired());
      case CONFLICT -> RFWReauthenticationOutcomeVO.conflict(errorConflict());
      case ACCESS_DENIED -> RFWReauthenticationOutcomeVO.accessDenied(errorAccessDenied());
      case UNAVAILABLE -> RFWReauthenticationOutcomeVO.unavailable(errorUnavailable());
    };
  }

  private static Set<RFWAuthenticationMethodEnum> methods(ReauthenticationResultVO result) {
    return result.allowedMethods().stream()
        .map(method -> RFWAuthenticationMethodEnum.valueOf(method.name()))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static RFWAuthenticatedPrincipalAdapter currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal
            ? principal : null;
  }

  private static String errorRejected() {
    return "ui.securitySettings.error.reauthenticationRejected";
  }

  private static String errorExpired() {
    return "ui.securitySettings.error.reauthenticationExpired";
  }

  private static String errorConflict() {
    return "ui.securitySettings.error.reauthenticationConflict";
  }

  private static String errorAccessDenied() {
    return "ui.securitySettings.error.reauthenticationAccessDenied";
  }

  private static String errorUnavailable() {
    return "ui.securitySettings.error.reauthenticationUnavailable";
  }

  private static CompletionStage<RFWReauthenticationOutcomeVO> completed(
      RFWReauthenticationOutcomeVO outcome) {
    return CompletableFuture.completedFuture(outcome);
  }
}
