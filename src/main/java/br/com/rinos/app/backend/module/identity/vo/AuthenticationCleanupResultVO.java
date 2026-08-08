package br.com.rinos.app.backend.module.identity.vo;

/**
 * Quantidades produzidas pela manutenção de fluxos e provas efêmeros.
 *
 * @param expired quantidade expirada logicamente
 * @param deleted quantidade removida após a retenção
 * @author Rodrigo Leitão
 * @since 2026-08-08
 */
public record AuthenticationCleanupResultVO(int expired, int deleted) {

  public AuthenticationCleanupResultVO {
    if (expired < 0 || deleted < 0) {
      throw new IllegalArgumentException("cleanup counters must not be negative");
    }
  }
}
