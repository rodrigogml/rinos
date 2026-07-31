package br.com.rinos.app.api.vo;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.RegistrationCancellationRequestStatusEnum;

/**
 * Resposta estruturalmente neutra da solicitação de cancelamento.
 *
 * @param status resultado público
 * @param challengeReference referência aleatória sem relação persistente
 * @param expiresAt expiração comunicada para a etapa de confirmação
 * @param fieldErrors erros públicos por campo
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCancellationRequestResultVO(
    RegistrationCancellationRequestStatusEnum status,
    String challengeReference,
    Instant expiresAt,
    Map<String, String> fieldErrors) {

  /**
   * Copia o mapa e impede respostas aceitas sem a mesma forma de continuação.
   */
  public RegistrationCancellationRequestResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    if (status == RegistrationCancellationRequestStatusEnum.REQUEST_ACCEPTED
        && (challengeReference == null || challengeReference.isBlank() || expiresAt == null)) {
      throw new IllegalArgumentException(
          "accepted cancellation request requires a neutral challenge");
    }
  }

  /**
   * Resume o resultado sem serializar a referência opaca da continuação.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "RegistrationCancellationRequestResultVO[status=" + status
        + ", challengeReference=REDACTED, expiresAt=" + expiresAt
        + ", fieldErrorCount=" + fieldErrors.size() + "]";
  }
}
