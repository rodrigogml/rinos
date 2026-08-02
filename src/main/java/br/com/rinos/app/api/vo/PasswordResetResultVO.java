package br.com.rinos.app.api.vo;

import java.time.Duration;
import java.util.Map;

import br.com.rinos.app.api.enums.PasswordResetStatusEnum;

/**
 * Resultado seguro da tentativa de redefinição.
 *
 * @param status estado público
 * @param fieldErrors erros localizáveis por campo
 * @param retryAfter tempo restante do bloqueio por origem
 * @author Rodrigo Leitão
 * @since 2026-08-02
 */
public record PasswordResetResultVO(
    PasswordResetStatusEnum status,
    Map<String, String> fieldErrors,
    Duration retryAfter) {

  /** Preserva a imutabilidade do mapa. */
  public PasswordResetResultVO {
    fieldErrors = Map.copyOf(fieldErrors);
  }
}
