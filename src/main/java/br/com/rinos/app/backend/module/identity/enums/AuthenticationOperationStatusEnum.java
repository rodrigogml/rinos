package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos seguros das operações sobre fluxos e provas.
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
