package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.api.enums.EmailOtpEmissionStatusEnum;

/**
 * Resultado público do envio sem destinatário completo, código, digest ou detalhes SMTP.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record EmailOtpEmissionResultVO(
    EmailOtpEmissionStatusEnum status,
    String challengeReference,
    String maskedDestination,
    Instant expiresAt,
    Instant resendAvailableAt,
    Instant retryAfter) {

  public EmailOtpEmissionResultVO {
    Objects.requireNonNull(status, "status must not be null");
    boolean emitted = challengeReference != null && maskedDestination != null
        && expiresAt != null && resendAvailableAt != null;
    if ((status == EmailOtpEmissionStatusEnum.EMITTED) != emitted
        || (status == EmailOtpEmissionStatusEnum.RATE_LIMITED) != (retryAfter != null)) {
      throw new IllegalArgumentException("public e-mail OTP result is inconsistent");
    }
  }

  public static EmailOtpEmissionResultVO terminal(EmailOtpEmissionStatusEnum status) {
    return new EmailOtpEmissionResultVO(status, null, null, null, null, null);
  }

  public static EmailOtpEmissionResultVO rateLimited(Instant retryAfter) {
    return new EmailOtpEmissionResultVO(
        EmailOtpEmissionStatusEnum.RATE_LIMITED, null, null, null, null, retryAfter);
  }
}
