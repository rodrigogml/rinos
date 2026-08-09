package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos seguros da emissão de OTP por e-mail.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public enum EmailOtpEmissionStatusEnum {
  EMITTED,
  RATE_LIMITED,
  REJECTED
}
