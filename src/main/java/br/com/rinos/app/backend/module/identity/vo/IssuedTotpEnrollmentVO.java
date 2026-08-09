package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrega uma única vez os dados necessários para apresentar e confirmar um enrollment TOTP.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record IssuedTotpEnrollmentVO(
    UUID reference,
    Instant expiresAt,
    String provisioningUri,
    String manualSecret) {

  /** Impede uma tentativa parcial sem referência, validade ou material de apresentação. */
  public IssuedTotpEnrollmentVO {
    Objects.requireNonNull(reference, "reference must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    if (provisioningUri == null || provisioningUri.isBlank()
        || manualSecret == null || manualSecret.isBlank()) {
      throw new IllegalArgumentException("TOTP presentation must be complete");
    }
  }

  @Override
  public String toString() {
    return "IssuedTotpEnrollmentVO[reference=" + reference + ", expiresAt=" + expiresAt
        + ", provisioningUri=REDACTED, manualSecret=REDACTED]";
  }
}
