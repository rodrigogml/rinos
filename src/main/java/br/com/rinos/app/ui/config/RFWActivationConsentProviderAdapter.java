package br.com.rinos.app.ui.config;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.facade.RegistrationActivationFacade;
import br.eng.rodrigogml.rfw.authentication.dto.RFWActivationConsentRequestDTO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWActivationConsentProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Adapta a continuação legal do RFW à fachada pública de ativação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWActivationConsentProviderAdapter
    implements RFWActivationConsentProvider {

  private final RegistrationActivationFacade facade;

  /**
   * Cria o adapter sem acesso direto ao backend.
   *
   * @param facade ativação pública do Rinos
   */
  public RFWActivationConsentProviderAdapter(
      @Lazy RegistrationActivationFacade facade) {
    this.facade = facade;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> completeActivationConsent(
      RFWActivationConsentRequestDTO request) {
    return facade.completeConsent(new ActivationConsentRequestDTO(
        request.activationReference(),
        request.acceptedLegalDocumentIds(),
        UUID.randomUUID()))
        .thenApply(RFWRegistrationProviderAdapter::mapActivation);
  }
}
