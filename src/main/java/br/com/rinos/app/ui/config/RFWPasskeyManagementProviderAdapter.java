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

import br.com.rinos.app.api.dto.PasskeyManagementContextDTO;
import br.com.rinos.app.api.dto.PasskeyRenameRequestDTO;
import br.com.rinos.app.api.dto.PasskeyRevocationRequestDTO;
import br.com.rinos.app.api.enums.PasskeyManagementStatusEnum;
import br.com.rinos.app.api.facade.PasskeyManagementFacade;
import br.com.rinos.app.api.vo.PasskeyManagementResultVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodStateEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPasskeyManagementProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationMethodVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecurityManagementOutcomeVO;

/**
 * Publica a gestao persistente de passkeys do Rinos ao componente de seguranca da RFW.
 *
 * <p>Identidade e sessao sao obtidas exclusivamente do contexto autenticado. O adapter nunca
 * transporta credential ID, chave publica, contador, attestation ou label em telemetria.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Component
public class RFWPasskeyManagementProviderAdapter implements RFWPasskeyManagementProvider {

  private final PasskeyManagementFacade facade;
  private final Clock clock;

  /** Cria o provider com relogio UTC. */
  @Autowired
  public RFWPasskeyManagementProviderAdapter(@Lazy PasskeyManagementFacade facade) {
    this(facade, Clock.systemUTC());
  }

  RFWPasskeyManagementProviderAdapter(PasskeyManagementFacade facade, Clock clock) {
    this.facade = facade;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<List<RFWAuthenticationMethodVO>>>
      listPasskeysOutcome() {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      List<RFWAuthenticationMethodVO> passkeys = facade.list(context(principal)).stream()
          .map(passkey -> {
            RFWAuthenticationMethodStateEnum state =
                RFWAuthenticationMethodStateEnum.valueOf(passkey.state().name());
            return new RFWAuthenticationMethodVO(
                passkey.reference(),
                RFWAuthenticationMethodEnum.PASSKEY,
                passkey.label(),
                state.isEnabled(),
                passkey.lastUsedAt(),
                passkey.createdAt(),
                state);
          })
          .toList();
      return completed(RFWSecurityManagementOutcomeVO.completed(passkeys));
    } catch (SecurityException denied) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<Void>> renamePasskeyOutcome(
      String methodId,
      String label) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      PasskeyManagementResultVO result = facade.rename(new PasskeyRenameRequestDTO(
          context(principal), methodId, label, UUID.randomUUID()));
      return completed(map(result));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<Void>> revokePasskeyOutcome(
      String methodId) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      PasskeyManagementResultVO result = facade.revoke(new PasskeyRevocationRequestDTO(
          context(principal), methodId, UUID.randomUUID()));
      return completed(map(result));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  private PasskeyManagementContextDTO context(RFWAuthenticatedPrincipalAdapter principal) {
    return new PasskeyManagementContextDTO(
        principal.user().userId(), principal.sessionReference(), clock.instant());
  }

  private static RFWSecurityManagementOutcomeVO<Void> map(PasskeyManagementResultVO result) {
    if (result == null) {
      return RFWSecurityManagementOutcomeVO.unavailable(unavailable());
    }
    return switch (result.status()) {
      case COMPLETED -> RFWSecurityManagementOutcomeVO.completed();
      case REJECTED -> RFWSecurityManagementOutcomeVO.rejected(rejected());
      case STALE -> RFWSecurityManagementOutcomeVO.stale(stale());
      case LAST_METHOD -> RFWSecurityManagementOutcomeVO.lastMethod(lastMethod());
      case ADMIN_FACTOR_REQUIRED, ACCESS_DENIED ->
          RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied());
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

  private static RFWAccessErrorVO stale() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.stale");
  }

  private static RFWAccessErrorVO lastMethod() {
    return RFWAccessErrorVO.of("ui.securitySettings.error.lastMethod");
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
