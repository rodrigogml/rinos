package br.com.rinos.app.backend.module.identity.enums;

/**
 * Efeitos estáveis e contáveis do lifecycle do cadastro.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum RegistrationLifecycleEventEnum {

  PENDING_CREATED,
  ACTIVATED,
  CANCELLED,
  EXPIRED,
  BLOCKED
}
