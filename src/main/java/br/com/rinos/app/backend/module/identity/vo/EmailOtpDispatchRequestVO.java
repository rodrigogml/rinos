package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Material efêmero entregue ao SMTP somente depois do commit da prova correspondente.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record EmailOtpDispatchRequestVO(
    String recipient,
    String code,
    Instant expiresAt,
    Locale locale,
    UUID correlationId) {

  public EmailOtpDispatchRequestVO {
    if (recipient == null || recipient.isBlank()) {
      throw new IllegalArgumentException("recipient must not be blank");
    }
    if (code == null || !code.matches("\\d{6,10}")) {
      throw new IllegalArgumentException("code must contain 6 to 10 digits");
    }
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    Objects.requireNonNull(correlationId, "correlationId must not be null");
  }

  @Override
  public String toString() {
    return "EmailOtpDispatchRequestVO[expiresAt=" + expiresAt + ", locale=" + locale
        + ", correlationId=" + correlationId + ", recipient=REDACTED, code=REDACTED]";
  }
}
