package br.com.rinos.app.api.dto;

import java.time.Instant;

/** Solicita enrollment TOTP para a identidade autenticada derivada pela interface. */
public record TotpEnrollmentRequestDTO(long userId, Instant occurredAt) {
}
