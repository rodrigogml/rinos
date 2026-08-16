package br.com.rinos.app.api.module.access.dto;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessAdministrationOrigin;
import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.vo.AuthorizationContext;

/** Criação ou substituição de regra direta ou de grupo. */
public record AccessRuleSaveRequest(
    AuthorizationContext context,
    long expectedRevision,
    AccessAdministrationOrigin origin,
    long originId,
    String accessKeyInternalReference,
    AccessRuleEffect effect,
    Instant validFrom,
    Instant validUntil,
    long actorUserId,
    String reason,
    String correlationId) {
}
