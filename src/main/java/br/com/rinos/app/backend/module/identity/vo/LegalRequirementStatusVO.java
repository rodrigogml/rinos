package br.com.rinos.app.backend.module.identity.vo;

import java.util.List;

/**
 * Informa se as versões obrigatórias vigentes possuem aceite.
 *
 * @param currentRequiredVersionIds versões obrigatórias vigentes
 * @param missingRequiredVersionIds versões que ainda exigem novo aceite
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record LegalRequirementStatusVO(
    List<Long> currentRequiredVersionIds,
    List<Long> missingRequiredVersionIds) {

  /**
   * Preserva listas imutáveis no contrato.
   */
  public LegalRequirementStatusVO {
    currentRequiredVersionIds = List.copyOf(currentRequiredVersionIds);
    missingRequiredVersionIds = List.copyOf(missingRequiredVersionIds);
  }

  /**
   * Indica mudança ou ausência de aceite obrigatório.
   *
   * @return {@code true} quando a ativação precisa solicitar decisões atuais
   */
  public boolean requiresConsent() {
    return !missingRequiredVersionIds.isEmpty();
  }
}
