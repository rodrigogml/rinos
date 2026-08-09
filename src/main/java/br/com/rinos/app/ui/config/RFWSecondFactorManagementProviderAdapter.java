package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.TotpEnrollmentCancellationDTO;
import br.com.rinos.app.api.dto.TotpEnrollmentConfirmationDTO;
import br.com.rinos.app.api.dto.TotpEnrollmentRequestDTO;
import br.com.rinos.app.api.enums.TotpEnrollmentStatusEnum;
import br.com.rinos.app.api.facade.TotpManagementFacade;
import br.com.rinos.app.api.vo.TotpEnrollmentResultVO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodStateEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWSecondFactorManagementProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationMethodVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecondFactorEnrollmentVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWSecurityManagementOutcomeVO;

/**
 * Publica o enrollment TOTP real do Rinos ao componente de segurança da RFW.
 *
 * <p>A identidade é sempre obtida do principal autenticado. O adapter não acessa persistência e
 * não registra o material de apresentação única.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
@ConditionalOnProperty(
    prefix = "rinos.authentication.keyring",
    name = "enabled",
    havingValue = "true")
public class RFWSecondFactorManagementProviderAdapter
    implements RFWSecondFactorManagementProvider {

  private final TotpManagementFacade facade;
  private final Clock clock;

  /** Cria o provider com relógio UTC. */
  @Autowired
  public RFWSecondFactorManagementProviderAdapter(@Lazy TotpManagementFacade facade) {
    this(facade, Clock.systemUTC());
  }

  RFWSecondFactorManagementProviderAdapter(TotpManagementFacade facade, Clock clock) {
    this.facade = facade;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<List<RFWAuthenticationMethodVO>>>
      listFactorsOutcome() {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      List<RFWAuthenticationMethodVO> factors = facade.listActive(principal.user().userId())
          .stream()
          .map(factor -> new RFWAuthenticationMethodVO(
              factor.reference(),
              RFWAuthenticationMethodEnum.TOTP,
              factor.label(),
              true,
              factor.lastUsedAt(),
              factor.createdAt(),
              RFWAuthenticationMethodStateEnum.ACTIVE))
          .toList();
      return completed(RFWSecurityManagementOutcomeVO.completed(factors));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecondFactorEnrollmentVO> beginEnrollmentDetails(
      RFWAuthenticationMethodEnum method) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null || method != RFWAuthenticationMethodEnum.TOTP) {
      return CompletableFuture.failedFuture(new SecurityException(
          "TOTP enrollment is unavailable for the current context"));
    }
    try {
      TotpEnrollmentResultVO result = facade.begin(new TotpEnrollmentRequestDTO(
          principal.user().userId(), clock.instant()));
      if (result.status() != TotpEnrollmentStatusEnum.PENDING) {
        return CompletableFuture.failedFuture(new IllegalStateException(
            "TOTP enrollment could not be started"));
      }
      return completed(new RFWSecondFactorEnrollmentVO(
          result.enrollmentReference(),
          RFWAuthenticationMethodEnum.TOTP,
          result.expiresAt(),
          result.provisioningUri(),
          result.manualSecret()));
    } catch (RuntimeException unavailable) {
      return CompletableFuture.failedFuture(new IllegalStateException(
          "TOTP enrollment is temporarily unavailable"));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<Void>> confirmEnrollmentOutcome(
      String challengeId,
      String proof) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal == null) {
      return completed(RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied()));
    }
    try {
      TotpEnrollmentResultVO result = facade.confirm(new TotpEnrollmentConfirmationDTO(
          principal.user().userId(), challengeId, proof, clock.instant()));
      return completed(mapConfirmation(result.status()));
    } catch (RuntimeException unavailable) {
      return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<Void> cancelEnrollment(String enrollmentReference) {
    RFWAuthenticatedPrincipalAdapter principal = currentPrincipal();
    if (principal != null) {
      try {
        facade.cancel(new TotpEnrollmentCancellationDTO(
            principal.user().userId(), enrollmentReference, clock.instant()));
      } catch (RuntimeException unavailable) {
        // A pendência possui validade persistida e não poderá ser confirmada depois do prazo.
      }
    }
    return CompletableFuture.completedFuture(null);
  }

  /** Remoção será publicada junto das invariantes completas da tarefa 4.1.5. */
  @Override
  public CompletionStage<RFWSecurityManagementOutcomeVO<Void>> removeFactorOutcome(String methodId) {
    return completed(RFWSecurityManagementOutcomeVO.unavailable(unavailable()));
  }

  private static RFWSecurityManagementOutcomeVO<Void> mapConfirmation(
      TotpEnrollmentStatusEnum status) {
    return switch (status) {
      case ACTIVE -> RFWSecurityManagementOutcomeVO.completed();
      case REJECTED -> RFWSecurityManagementOutcomeVO.rejected(rejected());
      case EXPIRED, ATTEMPTS_EXHAUSTED, STALE ->
          RFWSecurityManagementOutcomeVO.stale(stale());
      case ACCESS_DENIED -> RFWSecurityManagementOutcomeVO.insufficientAssurance(accessDenied());
      case UNAVAILABLE, PENDING -> RFWSecurityManagementOutcomeVO.unavailable(unavailable());
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
