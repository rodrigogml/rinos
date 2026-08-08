package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados fechados de uma prova de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationProofStatusEnum {

  OPEN,
  USED,
  INVALIDATED,
  EXPIRED
}
