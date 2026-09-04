package br.com.rinos.app.backend.module.access.service;

/** Resultado seguro de uma mutação corrente. */
public record AccessRuleMutationResult(long ruleId, long contextRevision, boolean changed) {
}
