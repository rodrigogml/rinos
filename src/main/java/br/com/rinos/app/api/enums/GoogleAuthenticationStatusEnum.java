package br.com.rinos.app.api.enums;

/**
 * Distingue um login Google processado da ausência que pode iniciar cadastro externo.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-10
 */
public enum GoogleAuthenticationStatusEnum {

  ORCHESTRATED,
  IDENTITY_NOT_FOUND
}
