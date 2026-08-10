package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.GoogleAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.enums.GoogleAuthenticationStatusEnum;
import br.com.rinos.app.api.facade.GoogleAuthenticationFacade;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(
      RFWExternalIdentityResolverAdapter.class);

  private final GoogleAuthenticationFacade authenticationFacade;
  private final GoogleIdentityResolutionFacade registrationFacade;
  private final RFWAuthenticationOutcomeAdapter outcomeAdapter;
  private final Clock clock;

  /**
   * Mantém a UI dependente somente da fachada pública.
   *
   * @param authenticationFacade autenticação por vínculo externo existente
   * @param registrationFacade decisão de cadastro para vínculo realmente ausente
   * @param outcomeAdapter mapeamento comum dos gates de autenticação
   */
  @Autowired
  public RFWExternalIdentityResolverAdapter(
      @Lazy GoogleAuthenticationFacade authenticationFacade,
      @Lazy GoogleIdentityResolutionFacade registrationFacade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter) {
    this(authenticationFacade, registrationFacade, outcomeAdapter, Clock.systemUTC());
  }

  /** Cria o adapter com relógio controlável para testes de fronteira. */
  RFWExternalIdentityResolverAdapter(
      GoogleAuthenticationFacade authenticationFacade,
      GoogleIdentityResolutionFacade registrationFacade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter,
      Clock clock) {
    this.authenticationFacade = authenticationFacade;
    this.registrationFacade = registrationFacade;
    this.outcomeAdapter = outcomeAdapter;
    this.clock = clock;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> resolve(
      RFWVerifiedExternalIdentityVO identity) {
    UUID correlationId = UUID.randomUUID();
    Instant validatedAt = clock.instant();
    try {
      GoogleAuthenticationRequestDTO authenticationRequest =
          new GoogleAuthenticationRequestDTO(
              identity.issuer(),
              identity.subject(),
              validatedAt,
              correlationId);
      return authenticationFacade.authenticate(authenticationRequest)
          .thenCompose(result -> {
            if (result.status() == GoogleAuthenticationStatusEnum.ORCHESTRATED) {
              return CompletableFuture.completedFuture(outcomeAdapter.map(
                  result.orchestration(), AuthenticationFlowPurposeEnum.SIGN_IN));
            }
            return continueRegistration(identity, correlationId);
          })
          .exceptionally(failure -> unavailable(correlationId, failure));
    } catch (RuntimeException failure) {
      return CompletableFuture.completedFuture(unavailable(correlationId, failure));
    }
  }

  private CompletionStage<RFWAuthenticationOutcomeVO> continueRegistration(
      RFWVerifiedExternalIdentityVO identity,
      UUID correlationId) {
    GoogleIdentityResolutionRequestVO request = new GoogleIdentityResolutionRequestVO(
            identity.providerId(),
            identity.issuer(),
            identity.subject(),
            identity.email(),
            identity.emailVerified(),
            correlationId);
    return registrationFacade.resolve(request).thenApply(this::mapRegistration);
  }

  private RFWAuthenticationOutcomeVO mapRegistration(
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

  private static RFWAuthenticationOutcomeVO unavailable(
      UUID correlationId,
      Throwable failure) {
    LOGGER.warn(
        "Resolucao Google indisponivel: correlationId={}, failureType={}",
        correlationId,
        failure.getClass().getSimpleName());
    return rejected("authentication.temporarily-unavailable");
  }
}
