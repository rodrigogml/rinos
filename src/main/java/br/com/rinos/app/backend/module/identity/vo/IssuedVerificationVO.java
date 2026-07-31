package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;

/**
 * Transporta efemeramente o token recém-emitido até o envio pós-commit.
 *
 * <p>Esta é a única fronteira interna que contém o token bruto. A representação textual é
 * deliberadamente redigida e o objeto nunca deve ser persistido, registrado ou exposto.
 *
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public final class IssuedVerificationVO {

  private final Long verificationId;
  private final String token;
  private final Instant expiresAt;

  /**
   * Cria o transporte efêmero de uma emissão concluída.
   *
   * @param verificationId identificador interno persistido
   * @param token segredo bruto de uso único
   * @param expiresAt limite UTC de consumo
   */
  public IssuedVerificationVO(Long verificationId, String token, Instant expiresAt) {
    this.verificationId =
        Objects.requireNonNull(verificationId, "verificationId must not be null");
    this.token = Objects.requireNonNull(token, "token must not be null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
  }

  /**
   * Retorna a identidade técnica da comprovação.
   *
   * @return identificador interno
   */
  public Long getVerificationId() {
    return verificationId;
  }

  /**
   * Retorna o token exclusivamente ao adaptador de envio pós-commit.
   *
   * @return segredo bruto de uso único
   */
  public String getToken() {
    return token;
  }

  /**
   * Retorna o limite de consumo.
   *
   * @return instante UTC
   */
  public Instant getExpiresAt() {
    return expiresAt;
  }

  /**
   * Produz representação segura sem o token.
   *
   * @return descrição redigida
   */
  @Override
  public String toString() {
    return "IssuedVerificationVO[verificationId="
        + verificationId
        + ", expiresAt="
        + expiresAt
        + ", token=REDACTED]";
  }
}
