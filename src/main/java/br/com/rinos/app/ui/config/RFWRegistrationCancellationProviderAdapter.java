package br.com.rinos.app.ui.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.facade.RegistrationCancellationFacade;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWRegistrationCancellationConfirmationDTO;
import br.eng.rodrigogml.rfw.platform.authentication.dto.RFWRegistrationCancellationRequestDTO;
import br.eng.rodrigogml.rfw.platform.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.platform.authentication.provider.RFWRegistrationCancellationProvider;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAccessChallengeVO;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.platform.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Adapta o cancelamento de cadastro do RFW à fachada pública do Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWRegistrationCancellationProviderAdapter
    implements RFWRegistrationCancellationProvider {

  private final RegistrationCancellationFacade facade;

  /**
   * Cria o adapter sem permitir que a UI dependa do backend.
   *
   * @param facade contrato público do cancelamento
   */
  public RFWRegistrationCancellationProviderAdapter(
      @Lazy RegistrationCancellationFacade facade) {
    this.facade = facade;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> requestCancellation(
      RFWRegistrationCancellationRequestDTO request) {
    return facade.requestCancellation(new RegistrationCancellationRequestDTO(
        request.identifier(),
        LocaleContextHolder.getLocale(),
        UUID.randomUUID())).thenApply(this::map);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> confirmCancellation(
      RFWRegistrationCancellationConfirmationDTO request) {
    return facade.confirmCancellation(new RegistrationCancellationConfirmationDTO(
        request.identifier(),
        request.proof(),
        UUID.randomUUID())).thenApply(this::map);
  }

  private RFWAuthenticationOutcomeVO map(
      RegistrationCancellationRequestResultVO result) {
    return switch (result.status()) {
      case REQUEST_ACCEPTED ->
          RFWAuthenticationOutcomeVO.registrationCancellationRequired(
              new RFWAccessChallengeVO(
                  result.challengeReference(),
                  RFWAuthenticationMethodEnum.EMAIL_CODE,
                  null,
                  result.expiresAt(),
                  Set.of(RFWAuthenticationMethodEnum.EMAIL_CODE)));
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.cancellation.validation-rejected",
              List.of(),
              result.fieldErrors(),
              null));
    };
  }

  private RFWAuthenticationOutcomeVO map(
      RegistrationCancellationConfirmationResultVO result) {
    return switch (result.status()) {
      case CANCELLED -> RFWAuthenticationOutcomeVO.completed(
          "registration.cancellation.completed");
      case INVALID_PROOF -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.cancellation.invalid-proof",
              List.of(),
              Map.of("proof", "registration.cancellation.invalid-proof"),
              null));
      case EXPIRED_PROOF -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.cancellation.expired-proof",
              List.of(),
              Map.of("proof", "registration.cancellation.expired-proof"),
              null));
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.cancellation.validation-rejected",
              List.of(),
              result.fieldErrors(),
              null));
      case UNAVAILABLE -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.unavailable"));
    };
  }
}
