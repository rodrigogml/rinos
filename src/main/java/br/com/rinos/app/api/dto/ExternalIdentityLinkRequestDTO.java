package br.com.rinos.app.api.dto;

import java.util.UUID;

/**
 * Solicita vínculo de uma identidade já validada pelo provider técnico.
 *
 * @param context contexto autenticado
 * @param providerId identificador fechado do provider
 * @param issuer emissor criptograficamente validado
 * @param subject identificador estável validado
 * @param explicitlyConfirmed confirmação explícita coletada pela interface
 * @param correlationId correlação de auditoria
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record ExternalIdentityLinkRequestDTO(
    ExternalIdentityManagementContextDTO context,
    String providerId,
    String issuer,
    String subject,
    boolean explicitlyConfirmed,
    UUID correlationId) {
}
