package br.com.rinos.app.backend.module.identity.enums;

/**
 * Decisões internas da resolução Google antes do mapeamento à API pública.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public enum GoogleIdentityDomainStatusEnum {

  CONTINUATION_REQUIRED,
  EXISTING_USER_REAUTHENTICATION_REQUIRED,
  EXTERNAL_IDENTITY_CONFLICT
}
