package br.com.rinos.app.backend.module.access.service;

import java.util.List;

import br.com.rinos.app.api.module.access.dto.AuthorizationRequest;
import br.com.rinos.app.api.module.access.vo.AuthorizationGateResult;

/** Porta de leitura da garantia recente, TOTP e passkey. */
public interface AuthorizationAssuranceGateProvider {
  List<AuthorizationGateResult> evaluate(AuthorizationRequest request);
}
