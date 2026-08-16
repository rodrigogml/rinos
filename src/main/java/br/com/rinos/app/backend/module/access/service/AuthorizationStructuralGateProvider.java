package br.com.rinos.app.backend.module.access.service;

import java.util.List;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;

/** Porta de leitura da identidade, associação, tenant e conta operacional. */
public interface AuthorizationStructuralGateProvider {
  List<AuthorizationGateResult> evaluate(AuthorizationRequest request);
}
