package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.RegistrationResendRequestDTO;
import br.com.rinos.app.api.vo.RegistrationResendResultVO;

/**
 * Publica o reenvio seguro de comprovação sem expor o estado persistente.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface RegistrationResendFacade {

  /**
   * Solicita uma nova comprovação e aguarda o resultado do despacho pós-commit.
   *
   * @param request solicitação sem prova anterior
   * @return resultado público neutro ou limitação temporária
   */
  CompletionStage<RegistrationResendResultVO> resend(
      RegistrationResendRequestDTO request);
}
