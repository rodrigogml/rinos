package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.RegistrationStartRequestDTO;
import br.com.rinos.app.api.vo.RegistrationStartResultVO;

/**
 * Publica o início do cadastro sem expor a persistência à apresentação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public interface RegistrationStartFacade {

  /**
   * Valida, persiste e aguarda o resultado do despacho pós-commit.
   *
   * @param request pedido efêmero
   * @return resultado concluído depois da aceitação ou falha SMTP
   */
  CompletionStage<RegistrationStartResultVO> start(RegistrationStartRequestDTO request);
}
