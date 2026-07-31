package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.ExternalRegistrationCompletionRequestDTO;
import br.com.rinos.app.api.vo.ExternalRegistrationCompletionResultVO;

/**
 * Fronteira pública para concluir um cadastro por identidade externa.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface ExternalRegistrationFacade {

  /**
   * Revalida a continuação e só publica principal depois da transação completa.
   *
   * @param request referência e aceites efêmeros
   * @return resultado assíncrono seguro
   */
  CompletionStage<ExternalRegistrationCompletionResultVO> complete(
      ExternalRegistrationCompletionRequestDTO request);
}
