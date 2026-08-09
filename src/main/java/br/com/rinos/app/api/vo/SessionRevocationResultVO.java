package br.com.rinos.app.api.vo;

/**
 * Resultado idempotente da revogação de sessões próprias.
 *
 * @param revokedCount quantidade efetivamente revogada
 * @param currentSessionRevoked indica necessidade de encerrar o contexto local
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record SessionRevocationResultVO(int revokedCount, boolean currentSessionRevoked) {

  public SessionRevocationResultVO {
    if (revokedCount < 0 || currentSessionRevoked && revokedCount == 0) {
      throw new IllegalArgumentException("revocation result is inconsistent");
    }
  }
}
