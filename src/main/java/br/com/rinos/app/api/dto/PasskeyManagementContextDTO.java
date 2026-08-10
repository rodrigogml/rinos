package br.com.rinos.app.api.dto;

import java.time.Instant;

/**
 * Identifica a sessao autenticada que solicita uma operacao de gestao de passkeys.
 *
 * @param userId identidade global autenticada
 * @param currentSessionReference referencia opaca da sessao corrente
 * @param occurredAt instante UTC da operacao
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeyManagementContextDTO(
    long userId,
    String currentSessionReference,
    Instant occurredAt) {
}
