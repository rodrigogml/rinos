package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultados internos da tentativa de emitir uma prova de cancelamento.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-01
 */
public enum RegistrationCancellationIssueStatusEnum {

  ISSUED,
  NOT_ELIGIBLE,
  RATE_LIMITED
}
