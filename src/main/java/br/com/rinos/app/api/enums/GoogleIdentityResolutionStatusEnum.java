package br.com.rinos.app.api.enums;

/**
 * Resultados públicos possíveis depois que o RFW valida uma identidade Google.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum GoogleIdentityResolutionStatusEnum {

  CONTINUATION_REQUIRED,
  EXISTING_USER_REAUTHENTICATION_REQUIRED,
  EXTERNAL_IDENTITY_CONFLICT,
  EXTERNAL_EMAIL_NOT_VERIFIED,
  EXTERNAL_IDENTITY_REJECTED,
  UNAVAILABLE
}
