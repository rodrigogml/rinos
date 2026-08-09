package br.com.rinos.app.api.facade;

import java.util.List;

import br.com.rinos.app.api.dto.SessionBulkRevocationRequestDTO;
import br.com.rinos.app.api.dto.SessionManagementContextDTO;
import br.com.rinos.app.api.dto.SessionRevocationRequestDTO;
import br.com.rinos.app.api.vo.AuthenticatedSessionVO;
import br.com.rinos.app.api.vo.SessionRevocationResultVO;

/**
 * Fronteira autenticada para reconhecer e encerrar sessões do próprio usuário.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public interface SessionManagementFacade {

  List<AuthenticatedSessionVO> list(SessionManagementContextDTO context);

  SessionRevocationResultVO revoke(SessionRevocationRequestDTO request);

  SessionRevocationResultVO revokeAll(SessionBulkRevocationRequestDTO request);
}
