package br.com.rinos.app.backend.module.access.service;

/** Resultado de uma mutação administrativa ACL confirmada. */
public record AccessAdministrationMutationResult(
    long targetId,
    long contextRevision,
    boolean changed) {
}
