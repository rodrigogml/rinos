package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.vo.GoogleIdentityResolutionRequestVO;
import br.com.rinos.app.api.vo.GoogleIdentityResolutionResultVO;

/**
 * Publica a decisão de cadastro para uma identidade Google já validada pelo RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface GoogleIdentityResolutionFacade {

  /**
   * Resolve vínculo, conflito ou continuação sem receber credencial externa.
   *
   * @param request atributos mínimos e já validados pelo provider
   * @return resultado assíncrono consumível pela UI
   */
  CompletionStage<GoogleIdentityResolutionResultVO> resolve(
      GoogleIdentityResolutionRequestVO request);
}
