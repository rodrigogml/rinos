package br.com.rinos.app.ui.config;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import br.com.rinos.app.api.dto.ExternalRegistrationCompletionRequestDTO;
import br.com.rinos.app.api.facade.ExternalRegistrationFacade;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;
import br.eng.rodrigogml.rfw.authentication.dto.RFWExternalRegistrationRequestDTO;
import br.eng.rodrigogml.rfw.authentication.provider.RFWExternalRegistrationProvider;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAccessErrorVO;
import br.eng.rodrigogml.rfw.authentication.vo.RFWAuthenticationOutcomeVO;

/**
 * Adapta a conclusão externa do RFW à fachada pública do Rinos.
 *
 * <p>O adapter nunca recebe ID token ou e-mail editável. A autenticação Spring é materializada
 * somente a partir do principal devolvido depois do commit do backend.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
@Component
public class RFWExternalRegistrationProviderAdapter
    implements RFWExternalRegistrationProvider {

  private final ExternalRegistrationFacade facade;

  /**
   * Mantém a interface dependente apenas do contrato público.
   *
   * @param facade conclusão transacional do cadastro
   */
  public RFWExternalRegistrationProviderAdapter(
      @Lazy ExternalRegistrationFacade facade) {
    this.facade = facade;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CompletionStage<RFWAuthenticationOutcomeVO> completeExternalRegistration(
      RFWExternalRegistrationRequestDTO request) {
    return facade.complete(new ExternalRegistrationCompletionRequestDTO(
        request.registrationReference(),
        request.acceptedLegalDocumentIds(),
        UUID.randomUUID())).thenApply(this::map);
  }

  private RFWAuthenticationOutcomeVO map(
      ExternalRegistrationCompletionResultVO result) {
    return switch (result.status()) {
      case AUTHENTICATED -> RFWAuthenticationOutcomeVO.authenticated(
          UsernamePasswordAuthenticationToken.authenticated(
              result.principal(),
              null,
              List.of()));
      case INVALID_REFERENCE -> rejected(
          "registration.google.completion.invalid-reference");
      case EXPIRED_REFERENCE -> rejected(
          "registration.google.completion.expired-reference");
      case VALIDATION_REJECTED -> RFWAuthenticationOutcomeVO.rejected(
          new RFWAccessErrorVO(
              "registration.validation-rejected",
              List.of(),
              result.fieldErrors(),
              null));
      case CONFLICT -> rejected(
          "registration.google.completion.conflict");
      case UNAVAILABLE -> rejected(
          "registration.google.unavailable");
    };
  }

  private static RFWAuthenticationOutcomeVO rejected(String messageKey) {
    return RFWAuthenticationOutcomeVO.rejected(
        RFWAccessErrorVO.of(messageKey));
  }
}
