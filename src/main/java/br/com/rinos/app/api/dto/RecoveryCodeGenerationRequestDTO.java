package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitação autenticada de geração ou substituição dos códigos de recuperação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record RecoveryCodeGenerationRequestDTO(
    long userId,
    UUID correlationId,
    Instant occurredAt) {
}
