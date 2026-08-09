package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos seguros da verificação de OTP por e-mail.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum EmailOtpVerificationStatusEnum {
  USED,
  REJECTED,
  EXPIRED,
  ATTEMPTS_EXHAUSTED,
  STALE
}
