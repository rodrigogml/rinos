package br.com.rinos.app.api.vo;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.RegistrationResendStatusEnum;

/**
 * Resultado público do reenvio sem identificador, prova ou entidade interna.
 *
 * @param status resultado catalogado
 * @param fieldErrors chaves i18n por campo
 * @param retryAfter espera mínima quando limitada
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationResendResultVO(
    RegistrationResendStatusEnum status,
    Map<String, String> fieldErrors,
    Duration retryAfter) {

  public RegistrationResendResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
  }

  /**
   * Cria um resultado sem erros de campo nem intervalo.
   *
   * @param status resultado obrigatório
   * @return valor público mínimo
   */
  public static RegistrationResendResultVO of(
      RegistrationResendStatusEnum status) {
    return new RegistrationResendResultVO(status, Map.of(), null);
  }
}
