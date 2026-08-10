package br.com.rinos.app.api.vo;

import java.time.Instant;

/**
 * Vínculo externo seguro para apresentação ao próprio usuário.
 *
 * @param reference referência opaca
 * @param providerId identificador público do provider
 * @param linkedAt instante da ativação mais recente
 * @param lastUsedAt último uso autenticado conhecido
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record ExternalIdentityVO(
    String reference,
    String providerId,
    Instant linkedAt,
    Instant lastUsedAt) {
}
