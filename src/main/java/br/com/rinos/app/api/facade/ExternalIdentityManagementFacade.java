package br.com.rinos.app.api.facade;

import java.util.List;

import br.com.rinos.app.api.dto.ExternalIdentityLinkRequestDTO;
import br.com.rinos.app.api.dto.ExternalIdentityManagementContextDTO;
import br.com.rinos.app.api.dto.ExternalIdentityUnlinkRequestDTO;
import br.com.rinos.app.api.vo.ExternalIdentityManagementResultVO;
import br.com.rinos.app.api.vo.ExternalIdentityVO;

/**
 * Publica a gestão autenticada de vínculos externos sem expor chaves internas.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public interface ExternalIdentityManagementFacade {

  /**
   * Lista os vínculos ativos do principal corrente.
   *
   * @param context contexto derivado da sessão
   * @return vínculos seguros
   */
  List<ExternalIdentityVO> list(ExternalIdentityManagementContextDTO context);

  /**
   * Vincula uma identidade validada e explicitamente confirmada.
   *
   * @param request dados reduzidos do provider
   * @return resultado público
   */
  ExternalIdentityManagementResultVO link(ExternalIdentityLinkRequestDTO request);

  /**
   * Revoga um vínculo próprio quando outro método utilizável permanece.
   *
   * @param request referência e contexto autenticado
   * @return resultado público
   */
  ExternalIdentityManagementResultVO unlink(ExternalIdentityUnlinkRequestDTO request);
}
