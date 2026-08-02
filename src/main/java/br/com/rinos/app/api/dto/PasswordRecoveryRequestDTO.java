package br.com.rinos.app.api.dto;

import java.util.Locale;
import java.util.UUID;

/**
 * Transporta uma solicitação pública sem afirmar a existência da identidade.
 *
 * @param identifier e-mail informado
 * @param canonicalOrigin origem validada pela borda HTTP
 * @param locale idioma da entrega
 * @param correlationId correlação técnica sem PII
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public record PasswordRecoveryRequestDTO(
    String identifier,
    String canonicalOrigin,
    Locale locale,
    UUID correlationId) {
}
