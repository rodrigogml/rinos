package br.com.rinos.app.ui.config;

import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.AuthenticationConsentRequestDTO;
import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;
import br.com.rinos.app.api.facade.AuthenticationConsentFacade;
import br.eng.rodrigogml.rfw.authentication.dto.RFWAuthenticationConsentRequestDTO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWAuthenticationConsentProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Liga o gate legal pós-fatores do RFW à continuação opaca do Rinos.
 *
 * <p>A conclusão ainda é anterior ao lifecycle oficial da sessão. O adapter não publica
 * contexto de segurança nem acrescenta authorities.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
@Component
public class RFWAuthenticationConsentProviderAdapter
    implements RFWAuthenticationConsentProvider {

  private final AuthenticationConsentFacade facade;
  private final RFWAuthenticationOutcomeAdapter outcomeAdapter;
  private final Clock clock;

  /** Cria o provider com relógio UTC. */
  @Autowired
  public RFWAuthenticationConsentProviderAdapter(
      @Lazy AuthenticationConsentFacade facade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter) {
    this(facade, outcomeAdapter, Clock.systemUTC());
  }

  RFWAuthenticationConsentProviderAdapter(
      AuthenticationConsentFacade facade,
      RFWAuthenticationOutcomeAdapter outcomeAdapter,
      Clock clock) {
    this.facade = facade;
    this.outcomeAdapter = outcomeAdapter;
    this.clock = clock;
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> completeAuthenticationConsent(
      RFWAuthenticationConsentRequestDTO request) {
    if (request == null) {
      return completed(unavailable());
    }
    try {
      return completed(outcomeAdapter.map(facade.complete(new AuthenticationConsentRequestDTO(
          request.continuationReference(),
          request.acceptedLegalDocumentIds(),
          clock.instant())), AuthenticationFlowPurposeEnum.SIGN_IN));
    } catch (RuntimeException unavailable) {
      return completed(unavailable());
    }
  }

  /** {@inheritDoc} */
  @Override
  public CompletionStage<Void> cancelAuthenticationConsent(String continuationReference) {
    try {
      facade.cancel(continuationReference, clock.instant());
    } catch (RuntimeException unavailable) {
      // Cancelamento continua sem publicar autenticação; a expiração persistente é o fallback seguro.
    }
    return CompletableFuture.completedFuture(null);
  }

  private static RFWAuthenticationOutcomeVO unavailable() {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of("authentication.temporarily-unavailable"));
  }

  private static CompletionStage<RFWAuthenticationOutcomeVO> completed(
      RFWAuthenticationOutcomeVO outcome) {
    return CompletableFuture.completedFuture(outcome);
  }
}
