package br.com.rinos.app.api.vo;

import java.time.Instant;

/** Fotografia pública de um fator TOTP ativo sem material criptográfico. */
public record TotpFactorVO(
    String reference,
    String label,
    Instant createdAt,
    Instant lastUsedAt) {
}
