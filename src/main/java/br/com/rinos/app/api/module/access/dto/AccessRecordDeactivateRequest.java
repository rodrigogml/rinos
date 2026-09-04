package br.com.rinos.app.api.module.access.dto;

import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Desativação lógica protegida pela revisão contextual observada. */
public record AccessRecordDeactivateRequest(
    AuthorizationContext context,
    long expectedRevision,
    long targetId,
    long actorUserId,
    String reason,
    String correlationId) {
}
