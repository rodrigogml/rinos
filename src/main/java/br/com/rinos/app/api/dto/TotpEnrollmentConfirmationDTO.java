package br.com.rinos.app.api.dto;

import java.time.Instant;

/** Transporta referência e prova efêmeras para confirmar um enrollment TOTP. */
public record TotpEnrollmentConfirmationDTO(
    long userId,
    String enrollmentReference,
    String proof,
    Instant occurredAt) {
}
