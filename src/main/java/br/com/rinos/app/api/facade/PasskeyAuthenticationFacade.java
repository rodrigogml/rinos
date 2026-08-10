package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.PasskeyAuthenticationRequestDTO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;

/**
 * Entrega uma identidade WebAuthn validada ao orquestrador único sem publicar sessão.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public interface PasskeyAuthenticationFacade {

  /**
   * Revalida o vínculo global e inicia os gates de garantia e consentimento.
   *
   * @param request prova reduzida e efêmera
   * @return decisão pública do orquestrador
   */
  CompletionStage<AuthenticationOrchestrationResultVO> authenticate(
      PasskeyAuthenticationRequestDTO request);
}
