package br.com.rinos.app.api.vo;

import java.io.Serial;
import java.io.Serializable;

/**
 * Principal mínimo de uma sessão autenticada do Rinos.
 *
 * <p>Não carrega entity, credencial, vínculo externo, tenant ou concessões. A autorização será
 * resolvida separadamente pelo contexto acessado.
 *
 * @param userId identificador global interno
 * @param email identificador primário exibível da própria sessão
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RinosUserPrincipalVO(long userId, String email) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Exige uma identidade persistida e um e-mail não vazio.
   */
  public RinosUserPrincipalVO {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("email must not be blank");
    }
  }

  /**
   * Evita expor a identidade da sessão quando o principal for incluído em diagnóstico.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RinosUserPrincipalVO[userId=REDACTED, email=REDACTED]";
  }
}
