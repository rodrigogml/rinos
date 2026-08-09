package br.com.rinos.app.backend.module.identity.enums;

/**
 * Resultado do cálculo de garantia recente para uma operação sensível.
 *
 * @author Rodrigo Leitão
 */
public enum ReauthenticationPolicyStatusEnum {
  ALREADY_RECENT,
  CHALLENGE_REQUIRED,
  ACCESS_DENIED
}
