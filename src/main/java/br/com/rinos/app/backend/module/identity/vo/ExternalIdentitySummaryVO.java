package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.ExternalIdentityProviderEnum;

/**
 * Fotografia segura de um vínculo externo sem issuer, subject, e-mail ou token.
 *
 * @param reference referência opaca de gestão
 * @param provider provedor reconhecido
 * @param linkedAt instante da ativação mais recente
 * @param lastUsedAt último uso autenticado conhecido
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record ExternalIdentitySummaryVO(
    UUID reference,
    ExternalIdentityProviderEnum provider,
    Instant linkedAt,
    Instant lastUsedAt) {
}
