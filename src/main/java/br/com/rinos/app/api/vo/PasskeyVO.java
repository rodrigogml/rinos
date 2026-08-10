package br.com.rinos.app.api.vo;

import java.time.Instant;

import br.com.rinos.app.api.enums.PasskeyStateEnum;

/**
 * Fotografia segura de uma passkey sem material WebAuthn.
 *
 * @param reference referencia opaca exclusiva de gestao
 * @param label nome reconhecivel pelo usuario
 * @param createdAt instante UTC do cadastro
 * @param lastUsedAt instante UTC do ultimo uso ou {@code null}
 * @param state estado apresentavel
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public record PasskeyVO(
    String reference,
    String label,
    Instant createdAt,
    Instant lastUsedAt,
    PasskeyStateEnum state) {
}
