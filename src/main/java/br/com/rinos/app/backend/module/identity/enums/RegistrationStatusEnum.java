package br.com.rinos.app.backend.module.identity.enums;

/**
 * Estados do processo temporário de cadastro de uma identidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationStatusEnum {

  PENDING_VERIFICATION,
  ACTIVE,
  CANCELLED,
  EXPIRED
}
