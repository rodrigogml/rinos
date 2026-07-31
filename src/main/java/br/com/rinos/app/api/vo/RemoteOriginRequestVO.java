package br.com.rinos.app.api.vo;

import java.util.Objects;

/**
 * Transporta somente os dados HTTP necessários para validar a origem de uma requisição.
 *
 * @param immediatePeer endereço da conexão TCP
 * @param forwarded cadeia RFC 7239 ou {@code null}
 * @param xForwardedFor cadeia legada ou {@code null}
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RemoteOriginRequestVO(
    String immediatePeer,
    String forwarded,
    String xForwardedFor) {

  /**
   * Exige a origem direta, que define se cabeçalhos encaminhados podem ser aceitos.
   */
  public RemoteOriginRequestVO {
    Objects.requireNonNull(immediatePeer, "immediatePeer não pode ser nulo.");
  }

  /**
   * Evita expor endereços ou cabeçalhos encaminhados em diagnóstico acidental.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RemoteOriginRequestVO[immediatePeer=REDACTED, forwarded=REDACTED, "
        + "xForwardedFor=REDACTED]";
  }
}
