package br.com.rinos.app.api.module.plans.vo;

import br.com.rinos.app.api.module.plans.enums.ContractScope;

/** Titular explícito de uma avaliação, sem inferência a partir do contexto global. */
public sealed interface EntitlementSubject
    permits PersonalEntitlementSubject, TenantEntitlementSubject {

  ContractScope scope();
}
