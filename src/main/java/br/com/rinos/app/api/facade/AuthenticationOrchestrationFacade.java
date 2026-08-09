package br.com.rinos.app.api.facade;

import java.time.Instant;

import br.com.rinos.app.api.dto.AuthenticationOrchestrationAdvanceDTO;
import br.com.rinos.app.api.dto.AuthenticationOrchestrationStartDTO;
import br.com.rinos.app.api.vo.AuthenticationOrchestrationResultVO;

/**
 * Fronteira única entre fatores validados e o lifecycle de sessão do RFW.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public interface AuthenticationOrchestrationFacade {

  /** Inicia o fluxo a partir de um primeiro fator já validado. */
  AuthenticationOrchestrationResultVO start(AuthenticationOrchestrationStartDTO request);

  /** Acrescenta um fator validado ao fluxo ainda aberto. */
  AuthenticationOrchestrationResultVO advance(AuthenticationOrchestrationAdvanceDTO request);

  /** Revalida uma conclusão pronta sem consumi-la antes do lifecycle da sessão. */
  AuthenticationOrchestrationResultVO complete(String reference, Instant occurredAt);

  /** Cancela idempotentemente uma continuação de login. */
  AuthenticationOrchestrationResultVO cancel(String reference, Instant occurredAt);
}
