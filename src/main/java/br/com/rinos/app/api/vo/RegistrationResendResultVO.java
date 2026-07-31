package br.com.rinos.app.api.vo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.RegistrationResendStatusEnum;

/**
 * Resultado público do reenvio sem identificador, prova ou entidade interna.
 *
 * @param status resultado catalogado
 * @param fieldErrors chaves i18n por campo
 * @param retryAfter espera mínima quando limitada
 * @param expiresAt expiração UTC da nova comprovação, quando houve emissão
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationResendResultVO(
    RegistrationResendStatusEnum status,
    Map<String, String> fieldErrors,
    Duration retryAfter,
    Instant expiresAt) {

  public RegistrationResendResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    if (expiresAt != null && status != RegistrationResendStatusEnum.REQUEST_ACCEPTED) {
      throw new IllegalArgumentException("expiresAt is only valid for REQUEST_ACCEPTED");
    }
  }

  /**
   * Mantém a construção de resultados que não publicam expiração.
   *
   * @param status resultado catalogado
   * @param fieldErrors erros públicos por campo
   * @param retryAfter espera mínima, quando limitada
   */
  public RegistrationResendResultVO(
      RegistrationResendStatusEnum status,
      Map<String, String> fieldErrors,
      Duration retryAfter) {
    this(status, fieldErrors, retryAfter, null);
  }

  /**
   * Cria um resultado sem erros de campo nem intervalo.
   *
   * @param status resultado obrigatório
   * @return valor público mínimo
   */
  public static RegistrationResendResultVO of(
      RegistrationResendStatusEnum status) {
    return new RegistrationResendResultVO(status, Map.of(), null, null);
  }

  /**
   * Cria a resposta neutra de reenvio contendo a expiração somente quando houve emissão real.
   *
   * @param expiresAt expiração UTC da nova comprovação
   * @return resultado aceito com apresentação temporal
   */
  public static RegistrationResendResultVO acceptedWithExpiration(Instant expiresAt) {
    return new RegistrationResendResultVO(
        RegistrationResendStatusEnum.REQUEST_ACCEPTED,
        Map.of(),
        null,
        Objects.requireNonNull(expiresAt, "expiresAt must not be null"));
  }
}
