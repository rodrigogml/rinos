package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Solicita a alteracao do nome reconhecivel de uma passkey propria.
 *
 * @param context contexto autenticado da operacao
 * @param passkeyReference referencia opaca da passkey
 * @param label novo nome reconhecivel pelo usuario
 * @param correlationId correlacao tecnica da alteracao
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeyRenameRequestDTO(
    PasskeyManagementContextDTO context,
    String passkeyReference,
    String label,
    UUID correlationId) {
}
