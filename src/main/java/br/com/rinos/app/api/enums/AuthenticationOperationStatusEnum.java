package br.com.rinos.app.api.enums;

/**
 * Resultado neutro de uma operação sobre fluxo ou prova.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationOperationStatusEnum {
  OPEN,
  USED,
  ALREADY_USED,
  INVALIDATED,
  EXPIRED,
  REJECTED
}
