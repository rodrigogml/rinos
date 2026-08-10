package br.com.rinos.app.api.facade;

import br.com.rinos.app.api.dto.RecoveryCodeGenerationRequestDTO;
import br.com.rinos.app.api.vo.RecoveryCodeGenerationResultVO;

/**
 * Fronteira pública para geração autenticada de códigos de recuperação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface RecoveryCodeManagementFacade {

  /**
   * Gera dez códigos para apresentação única e invalida o conjunto anterior.
   *
   * @param request identidade, correlação e instante da operação
   * @return resultado sanitizado ou apresentação única completa
   */
  RecoveryCodeGenerationResultVO generate(RecoveryCodeGenerationRequestDTO request);
}
