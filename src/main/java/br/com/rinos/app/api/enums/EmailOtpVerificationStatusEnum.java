package br.com.rinos.app.api.enums;

/**
 * Estados públicos da verificação de OTP por e-mail.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum EmailOtpVerificationStatusEnum {
  USED,
  REJECTED,
  EXPIRED,
  ATTEMPTS_EXHAUSTED,
  STALE,
  UNAVAILABLE
}
