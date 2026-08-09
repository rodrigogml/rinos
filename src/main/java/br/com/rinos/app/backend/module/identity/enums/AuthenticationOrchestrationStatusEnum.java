package br.com.rinos.app.backend.module.identity.enums;

/**
 * Decisões internas do orquestrador, anteriores à publicação da sessão.
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
