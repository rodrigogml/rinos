package br.com.rinos.app.api.dto;

import java.time.Instant;

/**
 * Contexto autenticado derivado pelo adapter para gerir identidades externas.
 *
 * @param userId usuário corrente
 * @param currentSessionReference referência opaca da sessão corrente
 * @param occurredAt instante UTC da operação
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record ExternalIdentityManagementContextDTO(
    long userId,
    String currentSessionReference,
    Instant occurredAt) {
}
