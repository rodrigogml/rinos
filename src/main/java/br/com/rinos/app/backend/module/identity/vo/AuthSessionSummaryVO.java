package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.UUID;

import br.com.rinos.app.backend.module.identity.enums.AuthenticationAssuranceEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthenticationMethodEnum;
import br.com.rinos.app.backend.module.identity.enums.AuthSessionStatusEnum;

/**
 * Projeção de gestão de sessão sem qualquer material autenticador.
 *
 * @param publicReference referência exclusiva para gestão
 * @param remembered indica política persistente
 * @param status estado corrente
 * @param primaryMethod método primário
 * @param assuranceLevel garantia calculada
 * @param authenticatedAt instante da autenticação
 * @param lastActivityAt última atividade persistida
 * @param absoluteExpiresAt limite absoluto
 * @param deviceDescription descrição sanitizada ou {@code null}
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthSessionSummaryVO(
    UUID publicReference,
    boolean remembered,
    AuthSessionStatusEnum status,
    AuthenticationMethodEnum primaryMethod,
    AuthenticationAssuranceEnum assuranceLevel,
    Instant authenticatedAt,
    Instant lastActivityAt,
    Instant absoluteExpiresAt,
    String deviceDescription) {
}
