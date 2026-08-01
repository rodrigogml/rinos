package br.com.rinos.app.ui.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import br.com.rinos.app.api.dto.RegistrationResendRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.com.rinos.app.api.facade.RegistrationResendFacade;
import br.com.rinos.app.api.facade.RegistrationStartFacade;
import br.com.rinos.app.api.vo.RegistrationResendResultVO;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWActivationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.enums.RFWAuthenticationMethodEnum;
import br.eng.rodrigogml.rfw.authentication.provider.RFWRegistrationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWActivationConsentChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.ui.access.provider.RFWRemoteAddressProvider;

/**
 * Adapta o início do cadastro do RFW à fachada pública do Rinos.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWRegistrationProviderAdapter implements RFWRegistrationProvider {

  private final RegistrationStartFacade facade;
  private final RegistrationResendFacade resendFacade;
  private final RegistrationActivationFacade activationFacade;
  private final RFWRemoteAddressProvider remoteAddressProvider;

  /**
   * Mantém a UI dependente somente dos contratos públicos das duas plataformas.
   *
   * @param facade início transacional do cadastro
   * @param remoteAddressProvider origem validada pela allowlist do Rinos
   */
  public RFWRegistrationProviderAdapter(
      @Lazy RegistrationStartFacade facade,
      @Lazy RegistrationResendFacade resendFacade,
      @Lazy RegistrationActivationFacade activationFacade,
      RFWRemoteAddressProvider remoteAddressProvider) {
    this.facade = facade;
    this.resendFacade = resendFacade;
    this.activationFacade = activationFacade;
    this.remoteAddressProvider = remoteAddressProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> register(
      RFWRegistrationRequestDTO request) {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return completed(unavailable());
    }
    String password = request.password();
    RegistrationStartRequestDTO command = new RegistrationStartRequestDTO(
        request.email(),
        password == null ? new char[0] : password.toCharArray(),
        request.acceptedLegalDocumentIds(),
        remoteAddressProvider.resolve(attributes.getRequest()),
        LocaleContextHolder.getLocale(),
        UUID.randomUUID());
    return facade.start(command).thenApply(this::map);
  }

  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> activate(
      RFWActivationRequestDTO request) {
    return activationFacade.activate(new RegistrationActivationRequestDTO(
        request.identifier(),
        request.proof(),
        UUID.randomUUID())).thenApply(RFWRegistrationProviderAdapter::mapActivation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> resendActivation(String identifier) {
    return resendFacade.resend(new RegistrationResendRequestDTO(
        identifier,
        LocaleContextHolder.getLocale(),
        UUID.randomUUID())).thenApply(this::map);
  }

  private RFWAuthenticationOutcomeVO map(RegistrationStartResultVO result) {
    return switch (result.status()) {
      case EMAIL_SENT -> RFWAuthenticationOutcomeVO.activationRequired(
          "registration.email-sent",
          activationChallenge(result.expiresAt()));
      case EMAIL_DISPATCH_FAILED -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.email-dispatch-failed"));
      case EMAIL_ALREADY_EXISTS -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.email-exists",
              List.of(),
              Map.of("email", "registration.error.email-exists"),
              null));
      case PENDING_ALREADY_EXISTS -> RFWAuthenticationOutcomeVO.activationRequired(
          "registration.pending-exists",
          null);
      case RATE_LIMITED -> RFWAuthenticationOutcomeVO.rateLimited(
          new RFWAccessErrorVO(
              "registration.rate-limited",
              List.of(),
              Map.of(),
              result.retryAfter()));
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.validation-rejected",
              List.of(),
              result.fieldErrors(),
              null));
      case UNAVAILABLE -> unavailable();
    };
  }

  private RFWAuthenticationOutcomeVO map(RegistrationResendResultVO result) {
    return switch (result.status()) {
      case REQUEST_ACCEPTED -> RFWAuthenticationOutcomeVO.activationRequired(
          "registration.activation-resent",
          activationChallenge(result.expiresAt()));
      case EMAIL_DISPATCH_FAILED -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.resend-email-dispatch-failed"));
      case RATE_LIMITED -> RFWAuthenticationOutcomeVO.rateLimited(
          new RFWAccessErrorVO(
              "registration.resend-rate-limited",
              List.of(),
              Map.of(),
              result.retryAfter()));
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.validation-rejected",
              List.of(),
              result.fieldErrors(),
              null));
      case UNAVAILABLE -> unavailable();
    };
  }

  private static RFWAuthenticationOutcomeVO unavailable() {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("registration.unavailable"));
  }

  private static RFWAccessChallengeVO activationChallenge(
      Instant expiresAt) {
    if (expiresAt == null) {
      return null;
    }
    return new RFWAccessChallengeVO(
        UUID.randomUUID().toString(),
        RFWAuthenticationMethodEnum.EMAIL_CODE,
        null,
        expiresAt,
        Set.of(RFWAuthenticationMethodEnum.EMAIL_CODE));
  }

  private static CompletionStage<RFWAuthenticationOutcomeVO> completed(
      RFWAuthenticationOutcomeVO outcome) {
    return CompletableFuture.completedFuture(outcome);
  }

  /**
   * Converte os estados públicos de ativação para a máquina de estados do RFW.
   *
   * @param result resultado da fachada pública
   * @return outcome correspondente
   */
  static RFWAuthenticationOutcomeVO mapActivation(
      RegistrationActivationResultVO result) {
    return switch (result.status()) {
      case ACTIVATED -> RFWAuthenticationOutcomeVO.completed(
          "registration.activation-completed");
      case ALREADY_ACTIVE -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.activation.used-proof"));
      case CONSENT_REQUIRED ->
          RFWAuthenticationOutcomeVO.activationConsentRequired(
              new RFWActivationConsentChallengeVO(
                  result.activationReference(),
                  result.verifiedEmail(),
                  result.legalDocumentIds(),
                  result.expiresAt()));
      case INVALID_PROOF -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.activation.invalid-proof"));
      case EXPIRED_PROOF -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.activation.expired-proof"));
      case REGISTRATION_CLOSED -> RFWAuthenticationOutcomeVO.rejected(
          RFWAccessErrorVO.of("registration.activation.registration-closed"));
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.validation-rejected",
              List.of(),
              result.fieldErrors(),
              null));
      case UNAVAILABLE -> unavailable();
    };
  }
}
