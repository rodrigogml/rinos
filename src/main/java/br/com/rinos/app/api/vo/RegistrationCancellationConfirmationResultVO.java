package br.com.rinos.app.api.vo;

import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.RegistrationCancellationConfirmationStatusEnum;

/**
 * Resultado seguro da confirmação do cancelamento.
 *
 * @param status resultado público
 * @param fieldErrors erros públicos por campo
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record RegistrationCancellationConfirmationResultVO(
    RegistrationCancellationConfirmationStatusEnum status,
    Map<String, String> fieldErrors) {

  /**
   * Protege o resultado contra mutação posterior.
   */
  public RegistrationCancellationConfirmationResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
  }

  /**
   * Cria um resultado sem erros de campo.
   *
   * @param status resultado público
   * @return valor imutável
   */
  public static RegistrationCancellationConfirmationResultVO of(
      RegistrationCancellationConfirmationStatusEnum status) {
    return new RegistrationCancellationConfirmationResultVO(status, Map.of());
  }
}
