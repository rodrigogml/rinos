package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados terminais e transitório de um fluxo de autenticação.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationFlowStatusEnum {

  OPEN,
  USED,
  INVALIDATED,
  EXPIRED
}
