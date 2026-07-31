package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;

import br.com.rinos.app.backend.module.identity.enums.IdentityTransitionOriginEnum;

/**
 * Descreve uma transição aplicada para posterior registro de auditoria.
 *
 * <p>O valor não contém e-mail, credencial, token ou outra evidência sensível.
 *
 * @param entityType tipo lógico da entidade alterada
 * @param previousStatus estado anterior persistido
 * @param newStatus novo estado persistido
 * @param origin origem da ação
 * @param reason motivo seguro e opcional
 * @param occurredAt instante UTC da alteração
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record IdentityTransitionVO(
    String entityType,
    String previousStatus,
    String newStatus,
    IdentityTransitionOriginEnum origin,
    String reason,
    Instant occurredAt) {
}
