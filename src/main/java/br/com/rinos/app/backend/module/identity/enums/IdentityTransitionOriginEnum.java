package br.com.rinos.app.backend.module.identity.enums;

/**
 * Origem responsável por uma transição de identidade ou cadastro.
 *
 * <p>O valor integra o contrato de auditoria e não concede autorização para executar a mudança.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum IdentityTransitionOriginEnum {

  SELF_SERVICE,
  EXTERNAL_PROVIDER,
  SCHEDULED_JOB,
  SYSTEM
}
