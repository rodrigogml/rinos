package br.com.rinos.app.backend.module.identity.vo;

/**
 * Resultado interno de uma revogação autenticada de sessões.
 *
 * @param revokedCount quantidade efetivamente transitada para revogada
 * @param currentSessionRevoked indica se a sessão solicitante estava no conjunto
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record AuthSessionRevocationVO(int revokedCount, boolean currentSessionRevoked) {

  public AuthSessionRevocationVO {
    if (revokedCount < 0 || currentSessionRevoked && revokedCount == 0) {
      throw new IllegalArgumentException("revocation result is inconsistent");
    }
  }
}
