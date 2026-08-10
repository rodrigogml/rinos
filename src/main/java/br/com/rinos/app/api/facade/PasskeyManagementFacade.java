package br.com.rinos.app.api.facade;

import java.util.List;

import br.com.rinos.app.api.dto.PasskeyManagementContextDTO;
import br.com.rinos.app.api.dto.PasskeyRenameRequestDTO;
import br.com.rinos.app.api.dto.PasskeyRevocationRequestDTO;
import br.com.rinos.app.api.vo.PasskeyManagementResultVO;
import br.com.rinos.app.api.vo.PasskeyVO;

/**
 * Fronteira autenticada para listar, nomear e revogar passkeys do proprio usuario.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public interface PasskeyManagementFacade {

  /**
   * Lista passkeys depois de validar a sessao corrente.
   *
   * @param context contexto autenticado
   * @return fotografias sem material WebAuthn
   */
  List<PasskeyVO> list(PasskeyManagementContextDTO context);

  /**
   * Renomeia uma passkey propria depois de revalidar autenticacao recente.
   *
   * @param request solicitacao autenticada
   * @return resultado publico
   */
  PasskeyManagementResultVO rename(PasskeyRenameRequestDTO request);

  /**
   * Revoga uma passkey propria depois de revalidar autenticacao recente e ultimo metodo.
   *
   * @param request solicitacao autenticada
   * @return resultado publico
   */
  PasskeyManagementResultVO revoke(PasskeyRevocationRequestDTO request);
}
