package br.com.rinos.app.api.dto;

import java.time.Instant;
import java.util.Locale;

/**
 * Solicitação pública de emissão vinculada ao fluxo opaco atual.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record EmailOtpEmissionRequestDTO(
    String challengeReference,
    Locale locale,
    Instant occurredAt) {
}
