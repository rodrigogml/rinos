package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;

import br.com.rinos.app.backend.module.access.enums.AccessAdministrationAction;

/** Comando interno de atribuição ou encerramento de sujeito em grupo. */
public record AccessGroupSubjectMutationCommand(
    AccessAdministrationAction action,
    Long groupSubjectId,
    Long groupId,
    Long userId,
    Long accountMembershipId,
    Instant validFrom,
    Instant validUntil,
    AccessMutationMetadata metadata) {
}
