package br.com.rinos.app.api.vo;

import java.io.Serial;
import java.io.Serializable;

import br.com.rinos.app.api.enums.AuthenticationFlowPurposeEnum;

/**
 * Continuação efêmera entregue ao lifecycle depois que o orquestrador retorna {@code READY}.
 *
 * <p>Este valor deve existir apenas na autenticação ainda não publicada. O principal final não
 * o preserva em {@code details}.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-09
 */
public record RinosAuthenticationCompletionVO(
    String flowReference,
    AuthenticationFlowPurposeEnum purpose) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public RinosAuthenticationCompletionVO {
    if (flowReference == null || flowReference.isBlank() || flowReference.length() > 512) {
      throw new IllegalArgumentException("flowReference is invalid");
    }
    if (purpose == null) {
      throw new IllegalArgumentException("purpose must not be null");
    }
  }

  /** Oculta a referência de conclusão em diagnósticos. */
  @Override
  public String toString() {
    return "RinosAuthenticationCompletionVO[flowReference=REDACTED, purpose=" + purpose + "]";
  }
}
