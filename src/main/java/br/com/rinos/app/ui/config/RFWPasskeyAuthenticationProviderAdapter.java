package br.com.rinos.app.ui.config;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.PasskeyAuthenticationRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.facade.PasskeyAuthenticationFacade;
import br.eng.rodrigogml.rfw.authentication.provider.RFWPasskeyAuthenticationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWValidatedPasskeyAuthenticationVO;

/**
 * Reduz a autenticação WebAuthn concreta do Spring ao contrato público mínimo do Rinos.
 *
 * <p>Somente o tipo produzido pelo provider WebAuthn e sua authority de fator são aceitos. Assertion, assinatura e
 * dados do cliente permanecem confinados ao endpoint Spring Security.</p>
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
@Component
public class RFWPasskeyAuthenticationProviderAdapter
    implements RFWPasskeyAuthenticationProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(
      RFWPasskeyAuthenticationProviderAdapter.class);

  private final PasskeyAuthenticationFacade facade;
  private final RFWAuthenticationOutcomeAdapter outcomeAdapter;

  /** Cria o provider sobre a fachada transacional e o mapper comum de outcomes. */
  public RFWPasskeyAuthenticationProviderAdapter(
      @Lazy PasskeyAuthenticationFacade facade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter) {
    this.facade = facade;
    this.outcomeAdapter = outcomeAdapter;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> authenticate(
      RFWValidatedPasskeyAuthenticationVO validatedAuthentication) {
    UUID correlationId = UUID.randomUUID();
    if (validatedAuthentication == null
        || !(validatedAuthentication.authentication() instanceof WebAuthnAuthentication authentication)
        || !hasWebAuthnFactor(authentication)) {
      return completed(rejected());
    }
    PublicKeyCredentialUserEntity principal = authentication.getPrincipal();
    if (principal == null || principal.getId() == null) {
      return completed(rejected());
    }
    try {
      PasskeyAuthenticationRequestDTO request = new PasskeyAuthenticationRequestDTO(
          principal.getId().getBytes(),
          validatedAuthentication.validatedAt(),
          correlationId);
      return facade.authenticate(request)
          .thenApply(result -> outcomeAdapter.map(result, AuthenticationFlowPurposeEnum.SIGN_IN))
          .exceptionally(failure -> unavailable(correlationId, failure));
    } catch (RuntimeException failure) {
      return completed(unavailable(correlationId, failure));
    }
  }

  private static boolean hasWebAuthnFactor(WebAuthnAuthentication authentication) {
    return authentication.getAuthorities().stream().anyMatch(authority ->
        FactorGrantedAuthority.WEBAUTHN_AUTHORITY.equals(authority.getAuthority()));
  }

  private static RFWAuthenticationOutcomeVO unavailable(UUID correlationId, Throwable failure) {
    LOGGER.warn(
        "Conclusão WebAuthn indisponível: correlationId={}, failureType={}",
        correlationId,
        failure.getClass().getSimpleName());
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.temporarily-unavailable"));
  }

  private static RFWAuthenticationOutcomeVO rejected() {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.credentials.invalid"));
  }

  private static CompletionStage<RFWAuthenticationOutcomeVO> completed(
      RFWAuthenticationOutcomeVO outcome) {
    return CompletableFuture.completedFuture(outcome);
  }
}
