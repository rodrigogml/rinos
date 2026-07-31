package br.com.rinos.app.api.vo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;

/**
 * Resultado público sem entities, credenciais ou identificadores internos.
 *
 * @param status resultado catalogado
 * @param fieldErrors chaves i18n por campo
 * @param retryAfter espera mínima quando limitada
 * @param expiresAt expiração UTC da comprovação emitida, quando conhecida
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationStartResultVO(
    RegistrationStartStatusEnum status,
    Map<String, String> fieldErrors,
    Duration retryAfter,
    Instant expiresAt) {

  public RegistrationStartResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    if ((status == RegistrationStartStatusEnum.EMAIL_SENT) != (expiresAt != null)) {
      throw new IllegalArgumentException("EMAIL_SENT requires expiresAt exclusively");
    }
  }

  /**
   * Mantém a construção de resultados que não publicam expiração.
   *
   * @param status resultado catalogado
   * @param fieldErrors erros públicos por campo
   * @param retryAfter espera mínima, quando limitada
   */
  public RegistrationStartResultVO(
      RegistrationStartStatusEnum status,
      Map<String, String> fieldErrors,
      Duration retryAfter) {
    this(status, fieldErrors, retryAfter, null);
  }

  public static RegistrationStartResultVO of(RegistrationStartStatusEnum status) {
    if (status == RegistrationStartStatusEnum.EMAIL_SENT) {
      throw new IllegalArgumentException("use emailSent for EMAIL_SENT results");
    }
    return new RegistrationStartResultVO(status, Map.of(), null, null);
  }

  /**
   * Cria o resultado aceito com a expiração real da comprovação enviada.
   *
   * @param expiresAt expiração UTC obrigatória
   * @return resultado de envio aceito
   */
  public static RegistrationStartResultVO emailSent(Instant expiresAt) {
    return new RegistrationStartResultVO(
        RegistrationStartStatusEnum.EMAIL_SENT,
        Map.of(),
        null,
        Objects.requireNonNull(expiresAt, "expiresAt must not be null"));
  }
}
