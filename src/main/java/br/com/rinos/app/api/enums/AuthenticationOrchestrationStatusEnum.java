package br.com.rinos.app.api.enums;

/**
 * Resultados fechados do orquestrador antes da publicação do contexto de segurança.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public enum AuthenticationOrchestrationStatusEnum {
  CHALLENGE_REQUIRED,
  LEGAL_CONSENT_REQUIRED,
  READY,
  CANCELLED,
  REJECTED,
  EXPIRED,
  CONFLICT,
  UNAVAILABLE
}
