package br.com.rinos.app.api.module.access.facade;

import br.com.rinos.app.api.module.access.dto.AccessExplanationRequest;
import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.vo.AccessExplanation;
import br.com.rinos.app.api.module.access.vo.AuthorizationDecision;

/** Fronteira única de decisão, exigência e explicação de autorização. */
public interface AuthorizationFacade {

  AuthorizationDecision decide(AuthorizationRequest request);

  AuthorizationDecision require(AuthorizationRequest request);

  AccessExplanation explain(AccessExplanationRequest request);
}
