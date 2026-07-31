package br.com.rinos.app.api.vo;

import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;

/**
 * Resultado seguro da conclusão externa e principal presente somente depois do commit.
 *
 * @param status resultado público
 * @param principal identidade mínima quando autenticada
 * @param fieldErrors erros públicos por campo
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record ExternalRegistrationCompletionResultVO(
    ExternalRegistrationCompletionStatusEnum status,
    RinosUserPrincipalVO principal,
    Map<String, String> fieldErrors) {

  /**
   * Impede principal em falhas e autenticação sem identidade confirmada.
   */
  public ExternalRegistrationCompletionResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    if ((status == ExternalRegistrationCompletionStatusEnum.AUTHENTICATED)
        != (principal != null)) {
      throw new IllegalArgumentException(
          "principal must be present only for authenticated result");
    }
  }

  /**
   * Cria um resultado terminal sem principal.
   *
   * @param status resultado diferente de autenticação
   * @return resultado minimizado
   */
  public static ExternalRegistrationCompletionResultVO of(
      ExternalRegistrationCompletionStatusEnum status) {
    return new ExternalRegistrationCompletionResultVO(status, null, Map.of());
  }

  /**
   * Cria uma rejeição estrutural por campo.
   *
   * @param fieldErrors chaves de mensagem por campo
   * @return rejeição segura
   */
  public static ExternalRegistrationCompletionResultVO validationRejected(
      Map<String, String> fieldErrors) {
    return new ExternalRegistrationCompletionResultVO(
        ExternalRegistrationCompletionStatusEnum.VALIDATION_REJECTED,
        null,
        fieldErrors);
  }

  /**
   * Publica o principal produzido pela transação concluída.
   *
   * @param principal identidade mínima
   * @return resultado autenticável
   */
  public static ExternalRegistrationCompletionResultVO authenticated(
      RinosUserPrincipalVO principal) {
    return new ExternalRegistrationCompletionResultVO(
        ExternalRegistrationCompletionStatusEnum.AUTHENTICATED,
        principal,
        Map.of());
  }

  /**
   * Resume o resultado sem serializar principal ou conteúdo dos erros.
   *
   * @return descrição estrutural sanitizada
   */
  @Override
  public String toString() {
    return "ExternalRegistrationCompletionResultVO[status=" + status
        + ", principalPresent=" + (principal != null)
        + ", fieldErrorCount=" + fieldErrors.size() + "]";
  }
}
