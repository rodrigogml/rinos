package br.com.rinos.app.api.vo;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.RegistrationStartStatusEnum;

/**
 * Resultado público sem entities, credenciais ou identificadores internos.
 *
 * @param status resultado catalogado
 * @param fieldErrors chaves i18n por campo
 * @param retryAfter espera mínima quando limitada
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationStartResultVO(
    RegistrationStartStatusEnum status,
    Map<String, String> fieldErrors,
    Duration retryAfter) {

  public RegistrationStartResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
  }

  public static RegistrationStartResultVO of(RegistrationStartStatusEnum status) {
    return new RegistrationStartResultVO(status, Map.of(), null);
  }
}
