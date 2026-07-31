package br.com.rinos.app.backend.module.identity.vo;

import java.time.Instant;
import java.util.Objects;

import br.com.rinos.app.backend.module.identity.enums.GoogleIdentityDomainStatusEnum;

/**
 * Resultado interno da resolução transacional da identidade externa.
 *
 * @param status decisão de domínio
 * @param continuationToken token efêmero, presente apenas na continuação
 * @param verifiedEmail e-mail verificado exibível
 * @param expiresAt validade da continuação
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record GoogleIdentityDomainResultVO(
    GoogleIdentityDomainStatusEnum status,
    String continuationToken,
    String verifiedEmail,
    Instant expiresAt) {

  /**
   * Impede que dados da continuação vazem em outros resultados internos.
   */
  public GoogleIdentityDomainResultVO {
    Objects.requireNonNull(status, "status must not be null");
    if (status == GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED) {
      if (continuationToken == null || continuationToken.isBlank()
          || verifiedEmail == null || verifiedEmail.isBlank()
          || expiresAt == null) {
        throw new IllegalArgumentException(
            "continuation requires token, verified email and expiration");
      }
    } else if (continuationToken != null || verifiedEmail != null || expiresAt != null) {
      throw new IllegalArgumentException(
          "non-continuation result must not contain continuation data");
    }
  }

  /**
   * Cria uma continuação a partir da prova recém-emitida.
   *
   * @param token token bruto mantido somente até a resposta
   * @param email e-mail verificado
   * @param expiresAt validade da prova
   * @return resultado interno
   */
  public static GoogleIdentityDomainResultVO continuation(
      String token,
      String email,
      Instant expiresAt) {
    return new GoogleIdentityDomainResultVO(
        GoogleIdentityDomainStatusEnum.CONTINUATION_REQUIRED,
        token,
        email,
        expiresAt);
  }

  /**
   * Cria uma decisão sem dados da identidade externa.
   *
   * @param status decisão sem continuação
   * @return resultado minimizado
   */
  public static GoogleIdentityDomainResultVO of(
      GoogleIdentityDomainStatusEnum status) {
    return new GoogleIdentityDomainResultVO(status, null, null, null);
  }

  /**
   * Produz representação segura sem o token de continuação ou o e-mail verificado.
   *
   * @return descrição redigida
   */
  @Override
  public String toString() {
    return "GoogleIdentityDomainResultVO[status="
        + status
        + ", continuationToken=REDACTED, verifiedEmail=REDACTED, expiresAt="
        + expiresAt
        + "]";
  }
}
