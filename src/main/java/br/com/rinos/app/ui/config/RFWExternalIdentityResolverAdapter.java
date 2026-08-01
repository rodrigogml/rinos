package br.com.rinos.app.ui.config;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.facade.GoogleIdentityResolutionFacade;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalIdentityResolver;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWExternalRegistrationChallengeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWVerifiedExternalIdentityVO;

/**
 * Reduz a identidade validada pelo RFW ao contrato público mínimo do Rinos.
 *
 * <p>O mapa de claims e qualquer credencial externa permanecem confinados ao RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWExternalIdentityResolverAdapter implements RFWExternalIdentityResolver {

  private final GoogleIdentityResolutionFacade facade;

  /**
   * Mantém a UI dependente somente da fachada pública.
   *
   * @param facade decisão de cadastro por identidade externa
   */
  public RFWExternalIdentityResolverAdapter(
      @Lazy GoogleIdentityResolutionFacade facade) {
    this.facade = facade;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> resolve(
      RFWVerifiedExternalIdentityVO identity) {
    GoogleIdentityResolutionRequestVO request =
        new GoogleIdentityResolutionRequestVO(
            identity.providerId(),
            identity.issuer(),
            identity.subject(),
            identity.email(),
            identity.emailVerified(),
            UUID.randomUUID());
    return facade.resolve(request).thenApply(this::map);
  }

  private RFWAuthenticationOutcomeVO map(
      GoogleIdentityResolutionResultVO result) {
    return switch (result.status()) {
      case CONTINUATION_REQUIRED ->
          RFWAuthenticationOutcomeVO.externalRegistrationRequired(
              new RFWExternalRegistrationChallengeVO(
                  result.registrationReference(),
                  result.providerId(),
                  result.verifiedEmail(),
                  result.expiresAt()));
      case EXISTING_USER_REAUTHENTICATION_REQUIRED ->
          rejected("registration.google.existing-user-reauthentication-required");
      case EXTERNAL_IDENTITY_CONFLICT ->
          rejected("registration.google.identity-conflict");
      case EXTERNAL_EMAIL_NOT_VERIFIED ->
          rejected("registration.google.email-not-verified");
      case EXTERNAL_IDENTITY_REJECTED ->
          rejected("registration.google.identity-rejected");
      case UNAVAILABLE ->
          rejected("registration.google.unavailable");
    };
  }

  private static RFWAuthenticationOutcomeVO rejected(String messageKey) {
    return RFWAuthenticationOutcomeVO.rejected(
        new RFWAccessErrorVO(messageKey, List.of(), Map.of(), null));
  }
}
