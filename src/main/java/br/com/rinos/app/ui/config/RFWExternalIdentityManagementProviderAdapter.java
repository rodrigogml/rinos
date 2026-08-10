package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.ExternalIdentityLinkRequestDTO;
import br.com.rinos.app.api.dto.ExternalIdentityManagementContextDTO;
import br.com.rinos.app.api.dto.ExternalIdentityUnlinkRequestDTO;
import br.com.rinos.app.api.facade.ExternalIdentityManagementFacade;
import br.com.rinos.app.api.vo.ExternalIdentityManagementResultVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodStateEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityManagementProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationMethodVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecurityManagementOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;

/**
 * Adapta a gestão RFW ao principal e à sessão autenticada do Rinos.
 *
 * <p>O adapter ignora e-mail e claims não necessários. O valor de confirmação explícita é
 * verdadeiro porque o RFW só chama {@code linkOutcome} depois de seu diálogo confirmado.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Component
public class RFWExternalIdentityManagementProviderAdapter
    implements RFWExternalIdentityManagementProvider {

  private final ExternalIdentityManagementFacade facade;
  private final Clock clock;

  /**
   * Cria o provider com relógio UTC.
   *
   * @param facade autoridade pública dos vínculos
   */
  @Autowired
  public RFWExternalIdentityManagementProviderAdapter(
      @Lazy ExternalIdentityManagementFacade facade) {
    this(facade, Clock.systemUTC());
  }

  RFWExternalIdentityManagementProviderAdapter(
      ExternalIdentityManagementFacade facade,
      Clock clock) {
    this.facade = facade;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<List<RFWAuthenticationMethodVO>>>
      listExternalIdentitiesOutcome() {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      List<RFWAuthenticationMethodVO> values = facade.list(context(principal)).stream()
          .map(identity -> new RFWAuthenticationMethodVO(
              identity.reference(),
              RFWAuthenticationMethodEnum.GOOGLE,
              "Google",
              true,
              identity.lastUsedAt(),
              identity.linkedAt(),
              RFWAuthenticationMethodStateEnum.ACTIVE))
          .toList();
      return completed(RFWSecurityManagementOutcomeVO.completed(values));
    } catch (SecurityException denied) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<Void>> linkOutcome(
      RFWVerifiedExternalIdentityVO identity) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null || identity == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      ExternalIdentityManagementResultVO result = facade.link(new ExternalIdentityLinkRequestDTO(
          context(principal),
          identity.providerId(),
          identity.issuer(),
          identity.subject(),
          true,
          UUID.randomUUID()));
      return completed(map(result));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<Void>> unlinkOutcome(String methodId) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      ExternalIdentityManagementResultVO result = facade.unlink(new ExternalIdentityUnlinkRequestDTO(
          context(principal), methodId, UUID.randomUUID()));
      return completed(map(result));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  private ExternalIdentityManagementContextDTO context(RFWAuthenticatedPrincipalAdapter principal) {
    return new ExternalIdentityManagementContextDTO(
        principal.user().userId(), principal.sessionReference(), clock.instant());
  }

  private static RFWSecurityManagementOutcomeVO<Void> map(
      ExternalIdentityManagementResultVO result) {
    if (result == null) {
      return RFWSecurityManagementOutcomeVO.unavailable(unavailable());
    }
    return switch (result.status()) {
      case COMPLETED -> RFWSecurityManagementOutcomeVO.completed();
      case REJECTED -> RFWSecurityManagementOutcomeVO.rejected(rejected());
      case CONFLICT -> RFWSecurityManagementOutcomeVO.conflict(conflict());
      case LAST_METHOD -> RFWSecurityManagementOutcomeVO.lastMethod(lastMethod());
      case STALE -> RFWSecurityManagementOutcomeVO.stale(stale());
      case ACCESS_DENIED -> RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied());
      case UNAVAILABLE -> RFWSecurityManagementOutcomeVO.unavailable(unavailable());
    };
  }

  private static RFWAuthenticatedPrincipalAdapter currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof RFWAuthenticatedPrincipalAdapter principal
            ? principal : null;
  }

  private static RFWAccessErrorVO rejected() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.rejected");
  }

  private static RFWAccessErrorVO conflict() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.conflict");
  }

  private static RFWAccessErrorVO lastMethod() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.lastMethod");
  }

  private static RFWAccessErrorVO stale() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.stale");
  }

  private static RFWAccessErrorVO accessDenied() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.insufficientAssurance");
  }

  private static RFWAccessErrorVO unavailable() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.unavailable");
  }

  private static <T> CompletionStage<T> completed(T value) {
    return CompletableFuture.completedFuture(value);
  }
}
