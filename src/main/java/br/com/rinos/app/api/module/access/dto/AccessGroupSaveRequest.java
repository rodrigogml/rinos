package br.com.rinos.app.api.module.access.dto;

import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Alteração de grupo protegida pela revisão observada pelo editor. */
public record AccessGroupSaveRequest(
    AuthorizationContext context,
    long expectedRevision,
    Long groupId,
    String name,
    String description,
    long actorUserId,
    String reason,
    String correlationId) {
}
