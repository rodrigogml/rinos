package br.com.rinos.app.api.dto;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Transporta prova e senha efêmeras para a redefinição.
 *
 * <p>A senha é transferida uma única vez e apagada da instância.
 *
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public final class PasswordResetRequestDTO {

  private final String proof;
  private final char[] password;
  private final String canonicalOrigin;
  private final UUID correlationId;

  /**
   * Cria o pedido com uma cópia defensiva da senha.
   *
   * @param proof prova opaca recebida no link
   * @param password senha cuja propriedade será transferida à fachada
   * @param canonicalOrigin origem validada pela borda HTTP
   * @param correlationId correlação técnica
   */
  public PasswordResetRequestDTO(
      String proof,
      char[] password,
      String canonicalOrigin,
      UUID correlationId) {
    this.proof = proof;
    this.password = Arrays.copyOf(
        Objects.requireNonNull(password, "password must not be null"),
        password.length);
    this.canonicalOrigin = canonicalOrigin;
    this.correlationId = Objects.requireNonNull(
        correlationId,
        "correlationId must not be null");
  }

  /** @return prova opaca efêmera */
  public String getProof() { return proof; }

  /** @return origem canônica validada */
  public String getCanonicalOrigin() { return canonicalOrigin; }

  /** @return correlação técnica */
  public UUID getCorrelationId() { return correlationId; }

  /**
   * Transfere a senha ao preparador e apaga a cópia mantida.
   *
   * @return cópia efêmera pertencente ao chamador
   */
  public char[] consumePassword() {
    char[] transferred = Arrays.copyOf(password, password.length);
    Arrays.fill(password, '\0');
    return transferred;
  }

  /** Impede exposição de prova, senha e origem. */
  @Override
  public String toString() {
    return "PasswordResetRequestDTO[proof=REDACTED, password=REDACTED, "
        + "canonicalOrigin=REDACTED, correlationId=" + correlationId + "]";
  }
}
