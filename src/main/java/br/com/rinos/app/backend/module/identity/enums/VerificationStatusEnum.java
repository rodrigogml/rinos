package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados persistentes de uma comprovação.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum VerificationStatusEnum {

  OPEN,
  USED,
  INVALIDATED,
  EXPIRED
}
