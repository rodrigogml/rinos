package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.api.module.access.enums.AccessScope;

/** Comando interno de remoção lógica de uma regra corrente. */
public record AccessRuleDeactivationCommand(
    long ruleId,
    AccessScope scope,
    Long tenantId,
    AccessMutationMetadata metadata) {
}
