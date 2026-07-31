package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.ActivationConsentRequestDTO;
import br.com.rinos.app.api.dto.RegistrationActivationRequestDTO;
import br.com.rinos.app.api.vo.RegistrationActivationResultVO;

/**
 * Publica a ativação local e sua eventual continuação de aceites.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface RegistrationActivationFacade {

  /**
   * Valida a prova e ativa ou solicita documentos legais atuais.
   *
   * @param request prova opaca
   * @return resultado assíncrono
   */
  CompletionStage<RegistrationActivationResultVO> activate(
      RegistrationActivationRequestDTO request);

  /**
   * Revalida a mesma prova, registra aceites e conclui a ativação.
   *
   * @param request referência e versões aceitas
   * @return resultado assíncrono
   */
  CompletionStage<RegistrationActivationResultVO> completeConsent(
      ActivationConsentRequestDTO request);
}
