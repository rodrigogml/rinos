package br.com.rinos.app.api.vo;

import java.time.Instant;

import br.com.rinos.app.api.enums.GoogleIdentityResolutionStatusEnum;

/**
 * Resultado público reduzido da resolução de uma identidade Google.
 *
 * @param status decisão de domínio
 * @param registrationReference referência opaca presente somente quando faltam aceites
 * @param providerId provedor exibível na continuação
 * @param verifiedEmail e-mail verificado e somente leitura na continuação
 * @param expiresAt validade da referência opaca
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record GoogleIdentityResolutionResultVO(
    GoogleIdentityResolutionStatusEnum status,
    String registrationReference,
    String providerId,
    String verifiedEmail,
    Instant expiresAt) {

  /**
   * Garante que somente uma continuação carregue os dados externos exibíveis.
   */
  public GoogleIdentityResolutionResultVO {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    if (status == GoogleIdentityResolutionStatusEnum.CONTINUATION_REQUIRED) {
      if (isBlank(registrationReference) || isBlank(providerId)
          || isBlank(verifiedEmail) || expiresAt == null) {
        throw new IllegalArgumentException(
            "continuation result requires reference, provider, email and expiration");
      }
    } else if (registrationReference != null || providerId != null
        || verifiedEmail != null || expiresAt != null) {
      throw new IllegalArgumentException(
          "non-continuation result must not expose continuation data");
    }
  }

  /**
   * Cria uma continuação opaca com o e-mail verificado.
   *
   * @param reference prova opaca de uso único
   * @param providerId provedor externo
   * @param verifiedEmail e-mail somente leitura
   * @param expiresAt validade da prova
   * @return resultado pronto para a UI
   */
  public static GoogleIdentityResolutionResultVO continuation(
      String reference,
      String providerId,
      String verifiedEmail,
      Instant expiresAt) {
    return new GoogleIdentityResolutionResultVO(
        GoogleIdentityResolutionStatusEnum.CONTINUATION_REQUIRED,
        reference,
        providerId,
        verifiedEmail,
        expiresAt);
  }

  /**
   * Cria uma decisão sem dados de continuação.
   *
   * @param status decisão pública diferente de continuação
   * @return resultado sem identidade, e-mail ou referência
   */
  public static GoogleIdentityResolutionResultVO of(
      GoogleIdentityResolutionStatusEnum status) {
    return new GoogleIdentityResolutionResultVO(status, null, null, null, null);
  }

  /**
   * Resume a decisão sem serializar referência opaca ou e-mail verificado.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "GoogleIdentityResolutionResultVO[status=" + status
        + ", continuationData=REDACTED, expiresAt=" + expiresAt + "]";
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
