package br.com.rinos.app.api.module.plans.facade;

import br.com.rinos.app.api.module.plans.dto.EntitlementEvaluationRequest;
import br.com.rinos.app.api.module.plans.vo.EntitlementDecision;

/** Fronteira pública e uniforme para decisões de direitos pessoais e tenant. */
public interface EntitlementEvaluationFacade {

  EntitlementDecision evaluate(EntitlementEvaluationRequest request);
}
