package br.com.rinos.app.backend.module.access.service;

import java.time.Instant;

import br.com.rinos.app.api.module.access.enums.AccessRuleEffect;
import br.com.rinos.app.api.module.access.enums.AccessScope;
import br.com.rinos.app.backend.module.access.enums.AccessRuleOriginType;

/** Entrada interna completa para criar ou substituir uma regra corrente. */
public record AccessRuleMutationCommand(
    AccessScope scope,
    Long tenantId,
    AccessRuleOriginType originType,
    Long userId,
    Long accountMembershipId,
    Long accessGroupId,
    String accessKeyCode,
    AccessRuleEffect effect,
    Instant validFrom,
    Instant validUntil,
    Long actorUserId,
    String systemOrigin,
    String reason,
    String correlationId,
    Instant occurredAt) {
}
