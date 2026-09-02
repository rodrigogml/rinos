package br.com.rinos.app.api.vo;

import java.util.Map;
import java.util.Objects;

import br.com.rinos.app.api.enums.ExternalRegistrationCompletionStatusEnum;

/**
 * Resultado seguro da conclusão externa e principal presente somente depois do commit.
 *
 * @param status resultado público
 * @param principal identidade mínima quando autenticada
 * @param authenticationContinuation fluxo opaco emitido depois da ativação externa
 * @param fieldErrors erros públicos por campo
 * @author Rodrigo Leitão
 * @since 2026-07-29
 */
public record ExternalRegistrationCompletionResultVO(
    ExternalRegistrationCompletionStatusEnum status,
    RinosUserPrincipalVO principal,
    RegistrationAuthenticationContinuationVO authenticationContinuation,
    Map<String, String> fieldErrors) {

  /**
   * Impede principal em falhas e autenticação sem identidade confirmada.
   */
  public ExternalRegistrationCompletionResultVO {
    status = Objects.requireNonNull(status, "status must not be null");
    fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    boolean authenticated = status == ExternalRegistrationCompletionStatusEnum.AUTHENTICATED;
    if (authenticated != (principal != null)
        || authenticated != (authenticationContinuation != null)) {
      throw new IllegalArgumentException(
          "authentication data must be present only for authenticated result");
    }
    if (authenticated && !principal.equals(authenticationContinuation.principal())) {
      throw new IllegalArgumentException("authentication continuation must belong to principal");
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
    return new ExternalRegistrationCompletionResultVO(status, null, null, Map.of());
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
        null,
        fieldErrors);
  }

  /**
   * Publica o principal produzido pela transação concluída.
   *
   * @param continuation principal e fluxo opaco persistidos
   * @return resultado autenticável
   */
  public static ExternalRegistrationCompletionResultVO authenticated(
      RegistrationAuthenticationContinuationVO continuation) {
    return new ExternalRegistrationCompletionResultVO(
        ExternalRegistrationCompletionStatusEnum.AUTHENTICATED,
        continuation.principal(),
        continuation,
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
        + ", authenticationContinuationPresent=" + (authenticationContinuation != null)
        + ", fieldErrorCount=" + fieldErrors.size() + "]";
  }
}
