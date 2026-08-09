package br.com.rinos.app.api.dto;

import java.time.Instant;

/** Solicita descarte de uma apresentação TOTP abandonada. */
public record TotpEnrollmentCancellationDTO(
    long userId,
    String enrollmentReference,
    Instant occurredAt) {
}
