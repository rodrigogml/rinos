package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.IdentityEventTypeEnum;

/**
 * Referência segura de um evento persistido.
 *
 * @param eventId identificador interno
 * @param correlationId correlação técnica
 * @param eventType tipo estável
 * @param occurredAt instante UTC
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record IdentityEventReferenceVO(
    Long eventId,
    UUID correlationId,
    IdentityEventTypeEnum eventType,
    Instant occurredAt) {
}
