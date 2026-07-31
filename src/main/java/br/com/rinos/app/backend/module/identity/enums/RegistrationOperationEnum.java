package br.com.rinos.app.backend.module.identity.enums;

/**
 * Operações estáveis do ciclo público de cadastro usadas na observabilidade.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationOperationEnum {

  START,
  RESEND,
  ACTIVATE,
  ACTIVATION_CONSENT,
  CANCELLATION_REQUEST,
  CANCELLATION_CONFIRM
}
