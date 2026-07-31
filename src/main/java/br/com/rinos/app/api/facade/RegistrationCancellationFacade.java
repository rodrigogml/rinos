package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.RegistrationCancellationConfirmationDTO;
import br.com.rinos.app.api.dto.RegistrationCancellationRequestDTO;
import br.com.rinos.app.api.vo.RegistrationCancellationConfirmationResultVO;
import br.com.rinos.app.api.vo.RegistrationCancellationRequestResultVO;

/**
 * Publica solicitação neutra e confirmação do cancelamento de cadastro pendente.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface RegistrationCancellationFacade {

  /**
   * Solicita uma prova sem revelar se o identificador possui pendência.
   *
   * @param request identificador e contexto efêmero
   * @return continuação com a mesma forma para identificadores existentes ou ausentes
   */
  CompletionStage<RegistrationCancellationRequestResultVO> requestCancellation(
      RegistrationCancellationRequestDTO request);

  /**
   * Confirma e remove atomicamente uma pendência controlada pela prova.
   *
   * @param request identificador e prova opaca
   * @return resultado seguro da confirmação
   */
  CompletionStage<RegistrationCancellationConfirmationResultVO> confirmCancellation(
      RegistrationCancellationConfirmationDTO request);
}
