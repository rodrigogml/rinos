package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.EmailOtpEmissionStatusEnum;

/**
 * Decisão interna de emissão, contendo material sensível somente quando realmente emitida.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record EmailOtpEmissionDecisionVO(
    EmailOtpEmissionStatusEnum status,
    IssuedEmailOtpVO issued,
    Instant retryAfter) {

  public EmailOtpEmissionDecisionVO {
    Objects.requireNonNull(status, "status must not be null");
    if ((status == EmailOtpEmissionStatusEnum.EMITTED) != (issued != null)
        || (status == EmailOtpEmissionStatusEnum.RATE_LIMITED) != (retryAfter != null)) {
      throw new IllegalArgumentException("e-mail OTP emission decision is inconsistent");
    }
  }

  public static EmailOtpEmissionDecisionVO emitted(IssuedEmailOtpVO issued) {
    return new EmailOtpEmissionDecisionVO(
        EmailOtpEmissionStatusEnum.EMITTED, Objects.requireNonNull(issued), null);
  }

  public static EmailOtpEmissionDecisionVO rateLimited(Instant retryAfter) {
    return new EmailOtpEmissionDecisionVO(
        EmailOtpEmissionStatusEnum.RATE_LIMITED, null, Objects.requireNonNull(retryAfter));
  }

  public static EmailOtpEmissionDecisionVO rejected() {
    return new EmailOtpEmissionDecisionVO(EmailOtpEmissionStatusEnum.REJECTED, null, null);
  }

  @Override
  public String toString() {
    return "EmailOtpEmissionDecisionVO[status=" + status + ", issued=REDACTED, retryAfter="
        + retryAfter + "]";
  }
}
