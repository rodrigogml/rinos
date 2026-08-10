package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.GoogleAuthenticationRequestDTO;
import br.com.rinos.app.api.vo.GoogleAuthenticationResultVO;

/**
 * Entrega uma identidade Google validada ao orquestrador único sem publicar sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public interface GoogleAuthenticationFacade {

  /**
   * Revalida o vínculo estável e inicia os gates de garantia e consentimento.
   *
   * @param request identidade reduzida e efêmera
   * @return decisão de login ou ausência segura para continuidade de cadastro
   */
  CompletionStage<GoogleAuthenticationResultVO> authenticate(
      GoogleAuthenticationRequestDTO request);
}
