package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Solicita a revogacao individual de uma passkey propria.
 *
 * @param context contexto autenticado da operacao
 * @param passkeyReference referencia opaca da passkey
 * @param correlationId correlacao tecnica da alteracao
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeyRevocationRequestDTO(
    PasskeyManagementContextDTO context,
    String passkeyReference,
    UUID correlationId) {
}
