package br.com.rinos.app.backend.module.identity.enums;

/**
 * Níveis fechados de garantia exigidos para concluir uma autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationAssuranceEnum {

  SINGLE_FACTOR,
  MULTI_FACTOR,
  PHISHING_RESISTANT
}
