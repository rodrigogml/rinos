package br.com.rinos.app.api.facade;

import br.com.rinos.app.api.dto.SecondFactorVerificationRequestDTO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;

/**
 * Fronteira pública da seleção e verificação contextual de segundo fator.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public interface SecondFactorFacade {

  /**
   * Consome a prova somente quando ela ainda pertence ao catálogo corrente do fluxo.
   *
   * @param request continuação, método, prova transitória e instante UTC
   * @return decisão pública do fluxo sem criar sessão diretamente
   */
  AuthenticationOrchestrationResultVO verify(SecondFactorVerificationRequestDTO request);
}
