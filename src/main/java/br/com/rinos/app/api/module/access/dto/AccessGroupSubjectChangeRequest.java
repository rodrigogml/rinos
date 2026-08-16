package br.com.rinos.app.api.module.access.dto;

import java.time.Instant;

import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Atribuição ou encerramento de participante com revisão contextual observada. */
public record AccessGroupSubjectChangeRequest(
    AuthorizationContext context,
    long expectedRevision,
    boolean assign,
    Long groupSubjectId,
    Long groupId,
    Long subjectId,
    Instant validFrom,
    Instant validUntil,
    long actorUserId,
    String reason,
    String correlationId) {
}
