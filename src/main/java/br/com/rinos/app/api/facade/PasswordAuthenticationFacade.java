package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.PasswordAuthenticationRequestDTO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;

/**
 * Autentica a credencial local e entrega seu primeiro fator ao orquestrador único.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface PasswordAuthenticationFacade {

  /**
   * Processa uma tentativa sem publicar diretamente sessão ou contexto de segurança.
   *
   * @param request tentativa efêmera
   * @return decisão pública do orquestrador
   */
  CompletionStage<AuthenticationOrchestrationResultVO> authenticate(
      PasswordAuthenticationRequestDTO request);
}
