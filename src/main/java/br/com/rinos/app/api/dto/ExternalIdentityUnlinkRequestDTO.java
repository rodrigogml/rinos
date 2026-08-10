package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Solicita a revogação de um vínculo externo próprio.
 *
 * @param context contexto autenticado
 * @param externalIdentityReference referência opaca apresentada pela listagem
 * @param correlationId correlação de auditoria
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record ExternalIdentityUnlinkRequestDTO(
    ExternalIdentityManagementContextDTO context,
    String externalIdentityReference,
    UUID correlationId) {
}
