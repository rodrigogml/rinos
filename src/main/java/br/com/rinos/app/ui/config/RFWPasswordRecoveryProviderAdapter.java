package br.com.rinos.app.ui.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.dto.PasswordRecoveryRequestDTO;
import br.com.rinos.app.api.dto.PasswordResetRequestDTO;
import br.com.rinos.app.api.facade.PasswordRecoveryFacade;
import br.com.rinos.app.api.vo.PasswordRecoveryRequestResultVO;
import br.com.rinos.app.api.vo.PasswordResetResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWPasswordResetRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRecoveryRequestDTO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPasswordRecoveryProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;

/**
 * Adapta a recuperação pública do RFW à fachada do Rinos sem expor o backend.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
@Component
public class RFWPasswordRecoveryProviderAdapter implements RFWPasswordRecoveryProvider {

  private final PasswordRecoveryFacade facade;
  private final RFWRemoteAddressProvider remoteAddressProvider;

  /**
   * Cria o adapter com resolução de origem compartilhada.
   *
   * @param facade contrato público da recuperação
   * @param remoteAddressProvider origem validada pela allowlist
   */
  public RFWPasswordRecoveryProviderAdapter(
      @Lazy PasswordRecoveryFacade facade,
      RFWRemoteAddressProvider remoteAddressProvider) {
    this.facade = facade;
    this.remoteAddressProvider = remoteAddressProvider;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> requestRecovery(
      RFWRecoveryRequestDTO request) {
    String origin = resolveOrigin();
    if (origin == null) {
      return completed(unavailable());
    }
    return facade.requestRecovery(new PasswordRecoveryRequestDTO(
        request.identifier(),
        origin,
        LocaleContextHolder.getLocale(),
        UUID.randomUUID())).thenApply(RFWPasswordRecoveryProviderAdapter::mapRequest);
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> resetPassword(
      RFWPasswordResetRequestDTO request) {
    String origin = resolveOrigin();
    if (origin == null) {
      return completed(unavailable());
    }
    String password = request.password();
    return facade.resetPassword(new PasswordResetRequestDTO(
        request.proof(),
        password == null ? new char[0] : password.toCharArray(),
        origin,
        UUID.randomUUID())).thenApply(RFWPasswordRecoveryProviderAdapter::mapReset);
  }

  static RFWAuthenticationOutcomeVO mapRequest(PasswordRecoveryRequestResultVO result) {
    return switch (result.status()) {
      case ACCEPTED -> RFWAuthenticationOutcomeVO.completed(
          "authentication.recovery.request-accepted");
      case RATE_LIMITED -> RFWAuthenticationOutcomeVO.rateLimited(new RFWAccessErrorVO(
          "authentication.recovery.rate-limited",
          List.of(),
          Map.of(),
          result.retryAfter()));
      case UNAVAILABLE -> unavailable();
    };
  }

  static RFWAuthenticationOutcomeVO mapReset(PasswordResetResultVO result) {
    return switch (result.status()) {
      case COMPLETED -> RFWAuthenticationOutcomeVO.completed(
          "authentication.recovery.completed");
      case INVALID_PROOF -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("authentication.recovery.invalid-proof"));
      case EXPIRED_PROOF -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("authentication.recovery.expired-proof"));
      case RATE_LIMITED -> RFWAuthenticationOutcomeVO.rateLimited(new RFWAccessErrorVO(
          "authentication.recovery.rate-limited",
          List.of(),
          Map.of(),
          result.retryAfter()));
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(new RFWAccessErrorVO(
          "authentication.recovery.validation-rejected",
          List.of(),
          result.fieldErrors(),
          null));
      case UNAVAILABLE -> unavailable();
    };
  }

  private String resolveOrigin() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return null;
    }
    return remoteAddressProvider.resolve(attributes.getRequest());
  }

  private static RFWAuthenticationOutcomeVO unavailable() {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.recovery.unavailable"));
  }

  private static CompletionStage<RFWAuthenticationOutcomeVO> completed(
      RFWAuthenticationOutcomeVO outcome) {
    return CompletableFuture.completedFuture(outcome);
  }
}
