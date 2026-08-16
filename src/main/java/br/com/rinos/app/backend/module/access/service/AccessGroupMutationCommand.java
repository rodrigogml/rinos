package br.com.rinos.app.backend.module.access.service;

import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;

/** Comando interno de criação, alteração ou desativação de grupo. */
public record AccessGroupMutationCommand(
    AccessAdministrationAction action,
    Long groupId,
    AccessScope scope,
    Long tenantId,
    String name,
    String description,
    boolean protectedGroup,
    Integer baselineVersion,
    AccessMutationMetadata metadata) {
}
