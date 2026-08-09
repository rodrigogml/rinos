package br.com.rinos.app.api.facade;

import java.time.Instant;

import br.com.rinos.app.api.dto.AuthenticationConsentRequestDTO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;

/**
 * Conclui ou cancela o gate legal sem publicar a sessão diretamente.
 *
 * @author Rodrigo Leitão
 */
public interface AuthenticationConsentFacade {

  /** Registra aceites atuais e entrega uma conclusão pronta ao lifecycle oficial. */
  AuthenticationOrchestrationResultVO complete(AuthenticationConsentRequestDTO request);

  /** Cancela idempotentemente a continuação sem alterar evidências anteriores. */
  AuthenticationOrchestrationResultVO cancel(String continuationReference, Instant occurredAt);
}
