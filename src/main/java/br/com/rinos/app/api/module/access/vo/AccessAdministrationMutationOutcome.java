package br.com.rinos.app.api.module.access.vo;

/** Resultado público mínimo de uma mutação administrativa. */
public record AccessAdministrationMutationOutcome(long targetId, long revision, boolean changed) {
}
