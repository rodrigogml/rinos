package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Material efêmero necessário para despachar um OTP já persistido de forma não recuperável.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record IssuedEmailOtpVO(
    String challengeReference,
    String recipient,
    String maskedDestination,
    String code,
    byte[] proofDigest,
    Instant expiresAt,
    Instant resendAvailableAt,
    UUID correlationId) {

  public IssuedEmailOtpVO {
    if (challengeReference == null || challengeReference.isBlank()
        || recipient == null || recipient.isBlank()
        || maskedDestination == null || maskedDestination.isBlank()
        || code == null || code.isBlank()) {
      throw new IllegalArgumentException("issued e-mail OTP must be complete");
    }
    if (proofDigest == null || proofDigest.length != 32) {
      throw new IllegalArgumentException("proofDigest must contain 32 bytes");
    }
    proofDigest = Arrays.copyOf(proofDigest, proofDigest.length);
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(resendAvailableAt, "resendAvailableAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  @Override
  public byte[] proofDigest() {
    return Arrays.copyOf(proofDigest, proofDigest.length);
  }

  @Override
  public String toString() {
    return "IssuedEmailOtpVO[challengeReference=" + challengeReference
        + ", maskedDestination=" + maskedDestination
        + ", expiresAt=" + expiresAt
        + ", resendAvailableAt=" + resendAvailableAt
        + ", correlationId=" + correlationId
        + ", recipient=REDACTED, code=REDACTED, proofDigest=REDACTED]";
  }
}
