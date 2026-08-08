package br.com.rinos.app.backend.module.identity.enums;

/**
 * Finalidades fechadas de uma continuação de autenticação.
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
