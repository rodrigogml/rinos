package br.com.rinos.app.api.enums;

/**
 * Estados públicos da tentativa de envio de OTP por e-mail.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum EmailOtpEmissionStatusEnum {
  EMITTED,
  RATE_LIMITED,
  REJECTED,
  UNAVAILABLE
}
