package br.com.rinos.app.api.facade;

import java.util.concurrent.CompletionStage;

import br.com.rinos.app.api.dto.PasswordRecoveryRequestDTO;
import br.com.rinos.app.api.dto.PasswordResetRequestDTO;
import br.com.rinos.app.api.vo.PasswordRecoveryRequestResultVO;
import br.com.rinos.app.api.vo.PasswordResetResultVO;

/**
 * Publica a recuperação mínima sem expor persistência à interface.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public interface PasswordRecoveryFacade {

  /**
   * Solicita instruções com resposta neutra.
   *
   * @param request entrada pública
   * @return resultado concluído depois do despacho pós-commit, quando houver
   */
  CompletionStage<PasswordRecoveryRequestResultVO> requestRecovery(
      PasswordRecoveryRequestDTO request);

  /**
   * Redefine a credencial mediante prova válida.
   *
   * @param request prova e nova senha efêmeras
   * @return resultado da transação
   */
  CompletionStage<PasswordResetResultVO> resetPassword(PasswordResetRequestDTO request);
}
