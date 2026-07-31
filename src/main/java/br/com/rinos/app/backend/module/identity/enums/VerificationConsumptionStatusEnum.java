package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados seguros do consumo de uma comprovação.
 *
 * <p>Os valores não revelam token, hash ou dados da identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum VerificationConsumptionStatusEnum {

  VERIFIED,
  ALREADY_USED,
  EXPIRED,
  REJECTED
}
