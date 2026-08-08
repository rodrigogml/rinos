package br.com.rinos.app.api.enums;

/**
 * Finalidade fechada de uma continuação opaca de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationFlowPurposeEnum {
  SIGN_IN,
  REAUTHENTICATION,
  FACTOR_RECOVERY,
  LEGAL_CONSENT
}
